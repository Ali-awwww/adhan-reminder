package com.adhanreminder.app.domain

import com.adhanreminder.app.data.AttemptDiagnostic
import com.adhanreminder.app.data.AttemptMode
import com.adhanreminder.app.data.AttemptProgress
import com.adhanreminder.app.data.GeminiClient
import com.adhanreminder.app.data.ImagePreparationResult
import com.adhanreminder.app.data.ModelConfig
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
    val activeModelName: String = ModelConfig.MODEL_NAME,
    val activeMode: AttemptMode = AttemptMode.SCHEMA,
    val attemptTimeoutSeconds: Int = (GeminiClient.REQUEST_TIMEOUT_MS / 1_000L).toInt(),
    val usedFallbackModel: Boolean = false,
    val imageBytes: Int? = null,
    val aiStartedAtMillis: Long? = null,
    val latencyMillis: Long? = null,
    val responseChars: Int? = null,
    val responsePreview: String? = null,
    val statusDetail: String? = null,
    val attempts: List<AttemptDiagnostic> = emptyList(),
    val isCancelled: Boolean = false,
)

class CapturePipeline(
    private val prepareImage: suspend () -> ImagePreparationResult,
    private val getQuotaRecord: suspend () -> ScanQuotaRecord,
    private val recordScanAttempt: suspend (Long, LocalDate) -> Unit,
    private val extractTimetable: suspend (
        ByteArray,
        (AttemptProgress) -> Unit,
    ) -> TimetableResult,
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

            val attemptCheckNow = nowMillisProvider()
            val attemptCheckToday = todayProvider()
            val attemptStatus = ScanQuotaPolicy.evaluate(getQuotaRecord(), attemptCheckNow, attemptCheckToday)
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

            val overallAiStart = nowMillisProvider()
            diag = diag.copy(
                activeModelName = ModelConfig.MODEL_NAME,
                activeMode = AttemptMode.SCHEMA,
                usedFallbackModel = false,
                aiStartedAtMillis = overallAiStart,
                latencyMillis = null,
                responseChars = null,
                attempts = emptyList(),
                isCancelled = false,
                statusDetail = "تم إرسال $sizeKb KB إلى ${ModelConfig.MODEL_NAME} · في انتظار الرد…",
            )
            onDiagnostics(diag)
            onStage(AnalysisStage.AnalyzingWithAi)

            val result = extractTimetable(prepared.jpeg) { progress ->
                val switchNow = nowMillisProvider()
                diag = diag.copy(
                    activeModelName = progress.activeModelName,
                    activeMode = progress.activeMode,
                    usedFallbackModel = diag.usedFallbackModel || progress.isFallback,
                    aiStartedAtMillis = switchNow,
                    attempts = progress.completedAttempts,
                    statusDetail = progress.reasonAr
                        ?: "تم إرسال $sizeKb KB إلى ${progress.activeModelName} (${progress.activeMode.labelAr}) · في انتظار الرد…",
                )
                onDiagnostics(diag)
            }
            val aiLatency = (nowMillisProvider() - overallAiStart).coerceAtLeast(0L)
            if (result !is TimetableResult.Success) {
                diag = diag.copy(
                    latencyMillis = aiLatency,
                    attempts = result.attempts,
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

            val rawJson = result.rawJson
            val preview = rawJson.replace('\n', ' ').trim().take(160)
            diag = diag.copy(
                activeModelName = result.modelUsed,
                activeMode = result.modeUsed,
                latencyMillis = aiLatency,
                responseChars = rawJson.length,
                responsePreview = preview,
                attempts = result.attempts,
                statusDetail = "استلم ${rawJson.length} حرفاً من ${result.modelUsed} (${result.modeUsed.labelAr})",
            )
            onDiagnostics(diag)
            onStage(AnalysisStage.ValidatingSchedule)

            val parsed = ExtractionParser.parse(rawJson).getOrElse { failure ->
                val msg = when (failure) {
                    is ExtractionFailure.NotAPrayerTimetable ->
                        "هذه الصورة لا تبدو جدول مواقيت. صوّر الجدول كاملاً."
                    is ExtractionFailure.InvalidDayCount ->
                        "عدد أيام الجدول المستخرج غير صالح (${failure.count})."
                    else ->
                        "تعذّر تحليل الجدول، راجع الصورة وحاول مجدداً."
                }
                val failureDetail = when (failure) {
                    is ExtractionFailure.NotAPrayerTimetable -> "صُنّفت الصورة كغير جدول (not_a_prayer_timetable)"
                    is ExtractionFailure.InvalidDayCount -> "عدد الأيام المستخرج = ${failure.count}"
                    is ExtractionFailure.MissingField -> "حقل مفقود: ${failure.field}"
                    is ExtractionFailure.MalformedJson -> "JSON غير صالح: ${failure.detail}"
                    else -> failure.javaClass.simpleName
                }
                diag = diag.copy(
                    responsePreview = preview,
                    attempts = result.attempts,
                    statusDetail = "فشل التحليل ($failureDetail)",
                )
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
            // Deduct quota only when a valid prayer timetable was successfully extracted and saved.
            recordScanAttempt(nowMillisProvider(), todayProvider())
            diag = diag.copy(
                attempts = result.attempts,
                statusDetail = "تم التحقق وحفظ ${saved.dayCount} يوماً بنجاح",
            )
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
