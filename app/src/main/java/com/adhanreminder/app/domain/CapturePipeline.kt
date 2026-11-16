package com.adhanreminder.app.domain

import com.adhanreminder.app.data.ImagePreparationResult
import com.adhanreminder.app.data.TimetableResult
import kotlinx.coroutines.CancellationException
import java.time.LocalDate

sealed interface AnalysisStage {
    data object Idle : AnalysisStage
    data object PreviewReady : AnalysisStage
    data object PreparingImage : AnalysisStage
    data object AnalyzingWithAi : AnalysisStage
    data object ValidatingSchedule : AnalysisStage
    data class Success(val scheduleId: Long, val dayCount: Int) : AnalysisStage
    data class Error(val message: String, val canRetryWithSameImage: Boolean = true) : AnalysisStage

    val isBusy: Boolean
        get() = this is PreparingImage || this is AnalyzingWithAi || this is ValidatingSchedule
}

data class PersistedDraftResult(
    val scheduleId: Long,
    val dayCount: Int,
)

data class CaptureDiagnostics(
    val appCheckLabel: String = "",
    val appCheckDetail: String? = null,
    val imageBytes: Int? = null,
    val aiStartedAtMillis: Long? = null,
    val latencyMillis: Long? = null,
    val responseChars: Int? = null,
    val statusDetail: String? = null,
    val isCancelled: Boolean = false,
)

class CapturePipeline(
    private val prepareImage: suspend () -> ImagePreparationResult,
    private val getQuotaRecord: suspend () -> ScanQuotaRecord,
    private val recordScanAttempt: suspend (Long, LocalDate) -> Unit,
    private val extractTimetable: suspend (ByteArray) -> TimetableResult,
    private val saveValidatedSchedule: suspend (ParsedTimetable, ValidatedTimetable) -> PersistedDraftResult,
    private val nowMillisProvider: () -> Long = { System.currentTimeMillis() },
    private val todayProvider: () -> LocalDate = { LocalDate.now() },
) {
    suspend fun execute(
        initialDiagnostics: CaptureDiagnostics = CaptureDiagnostics(),
        onDiagnostics: (CaptureDiagnostics) -> Unit = {},
        onStage: (AnalysisStage) -> Unit,
    ): AnalysisStage {
        var diag = initialDiagnostics
        return try {
            val initialNow = nowMillisProvider()
            val initialToday = todayProvider()
            val preStatus = ScanQuotaPolicy.evaluate(getQuotaRecord(), initialNow, initialToday)
            if (!preStatus.canScan) {
                val reason = preStatus.blockReasonAr ?: "تعذر الإرسال حالياً."
                diag = diag.copy(statusDetail = reason)
                onDiagnostics(diag)
                val blocked = AnalysisStage.Error(
                    message = reason,
                    canRetryWithSameImage = true,
                )
                onStage(blocked)
                return blocked
            }

            diag = diag.copy(statusDetail = "جارٍ تجهيز وضغط الصورة…")
            onDiagnostics(diag)
            onStage(AnalysisStage.PreparingImage)
            val prepared = prepareImage()
            if (prepared !is ImagePreparationResult.Success) {
                diag = diag.copy(statusDetail = "تعذّر فك تشفير الصورة أو تجاوزت الحد المسموح (4 MB)")
                onDiagnostics(diag)
                val err = AnalysisStage.Error(
                    message = "تعذّر فتح الصورة أو أن حجمها كبير، جرّب صورة أخرى.",
                    canRetryWithSameImage = false,
                )
                onStage(err)
                return err
            }

            val sizeKb = maxOf(1, (prepared.jpeg.size + 512) / 1024)
            diag = diag.copy(
                imageBytes = prepared.jpeg.size,
                statusDetail = "تم تجهيز JPEG بحجم $sizeKb KB",
            )
            onDiagnostics(diag)

            val attemptNow = nowMillisProvider()
            val attemptToday = todayProvider()
            val attemptStatus = ScanQuotaPolicy.evaluate(getQuotaRecord(), attemptNow, attemptToday)
            if (!attemptStatus.canScan) {
                val reason = attemptStatus.blockReasonAr ?: "تعذر الإرسال حالياً."
                diag = diag.copy(statusDetail = reason)
                onDiagnostics(diag)
                val blocked = AnalysisStage.Error(
                    message = reason,
                    canRetryWithSameImage = true,
                )
                onStage(blocked)
                return blocked
            }
            recordScanAttempt(attemptNow, attemptToday)

            val aiStart = nowMillisProvider()
            diag = diag.copy(
                aiStartedAtMillis = aiStart,
                latencyMillis = null,
                responseChars = null,
                isCancelled = false,
                statusDetail = "تم إرسال $sizeKb KB · في انتظار رد النموذج…",
            )
            onDiagnostics(diag)
            onStage(AnalysisStage.AnalyzingWithAi)

            val result = extractTimetable(prepared.jpeg)
            val aiLatency = (nowMillisProvider() - aiStart).coerceAtLeast(0L)
            val rawJson = (result as? TimetableResult.Success)?.rawJson
            if (rawJson == null) {
                diag = diag.copy(
                    latencyMillis = aiLatency,
                    statusDetail = result.diagnosticDetail ?: result.userMessage,
                )
                onDiagnostics(diag)
                val err = AnalysisStage.Error(
                    message = result.userMessage,
                    canRetryWithSameImage = true,
                )
                onStage(err)
                return err
            }

            diag = diag.copy(
                latencyMillis = aiLatency,
                responseChars = rawJson.length,
                statusDetail = "استلم ${rawJson.length} حرفاً من النموذج",
            )
            onDiagnostics(diag)
            onStage(AnalysisStage.ValidatingSchedule)

            val parsed = ExtractionParser.parse(rawJson).getOrElse { failure ->
                val msg = when (failure) {
                    is ExtractionFailure.NotAPrayerTimetable ->
                        "هذه الصورة لا تبدو جدول مواقيت. صوّر الجدول كاملاً."
                    is ExtractionFailure.InvalidDayCount ->
                        "عدد أيام الجدول غير صالح."
                    else ->
                        "تعذّر تحليل الجدول، راجع الصورة وحاول مجدداً."
                }
                diag = diag.copy(statusDetail = "فشل تحليل JSON: ${failure.javaClass.simpleName}")
                onDiagnostics(diag)
                val err = AnalysisStage.Error(
                    message = msg,
                    canRetryWithSameImage = failure !is ExtractionFailure.NotAPrayerTimetable,
                )
                onStage(err)
                return err
            }

            val validated = TimetableValidator.validate(parsed)
            val saved = saveValidatedSchedule(parsed, validated)
            diag = diag.copy(statusDetail = "تم التحقق وحفظ ${saved.dayCount} يوماً بنجاح")
            onDiagnostics(diag)
            val success = AnalysisStage.Success(saved.scheduleId, saved.dayCount)
            onStage(success)
            success
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            diag = diag.copy(
                statusDetail = "خطأ غير متوقع: ${e.localizedMessage?.take(120) ?: e.javaClass.simpleName}",
            )
            onDiagnostics(diag)
            val err = AnalysisStage.Error(
                message = "تعذّرت قراءة الجدول. تحقق من الاتصال والصورة وحاول مجدداً.",
                canRetryWithSameImage = true,
            )
            onStage(err)
            err
        }
    }
}
