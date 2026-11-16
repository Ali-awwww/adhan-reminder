package com.adhanreminder.app.data

import android.util.Log
import com.adhanreminder.app.BuildConfig
import com.adhanreminder.app.domain.ExtractionFailure
import com.adhanreminder.app.domain.ExtractionParser
import com.adhanreminder.app.domain.ExtractionPrompt
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Content
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.IOException

object ModelConfig {
    const val MODEL_NAME = "gemini-3.8-flash"
    const val FALLBACK_MODEL_NAME = "gemini-3.5-flash-lite"
}

enum class AttemptMode(val labelAr: String) {
    SCHEMA("Schema"),
    PLAIN_JSON("JSON نصي"),
    TEXT_PING("فحص نصي"),
}

data class AttemptDiagnostic(
    val modelName: String,
    val mode: AttemptMode,
    val elapsedMillis: Long,
    val succeeded: Boolean,
    val rawDetail: String,
)

data class AttemptProgress(
    val activeModelName: String,
    val activeMode: AttemptMode,
    val isFallback: Boolean,
    val reasonAr: String? = null,
    val completedAttempts: List<AttemptDiagnostic> = emptyList(),
)

data class ServerProbeResult(
    val checkedAtMillis: Long,
    val appCheckLabel: String,
    val attempts: List<AttemptDiagnostic>,
) {
    val anySucceeded: Boolean get() = attempts.any { it.succeeded }
}

sealed class TimetableResult(open val userMessage: String) {
    open val diagnosticDetail: String? get() = null
    open val attempts: List<AttemptDiagnostic> get() = emptyList()

    data class Success(
        val rawJson: String,
        val modelUsed: String = ModelConfig.MODEL_NAME,
        val modeUsed: AttemptMode = AttemptMode.SCHEMA,
        override val attempts: List<AttemptDiagnostic> = emptyList(),
    ) : TimetableResult("تمت قراءة الجدول.")

    data class QuotaExceeded(
        val rawError: String = "Quota / 429",
        override val attempts: List<AttemptDiagnostic> = emptyList(),
    ) : TimetableResult("تجاوزت الحد المتاح مؤقتاً. لم يُخصم من رصيدك، حاول لاحقاً.") {
        override val diagnosticDetail: String get() = rawError
    }

    data class ServiceUnavailable(
        val rawError: String = "Service unavailable / 503",
        override val attempts: List<AttemptDiagnostic> = emptyList(),
    ) : TimetableResult("تعذّرت الاستجابة من الخادم بعد محاولة البدائل دون خصم رصيدك. راجع تفاصيل التشخيص.") {
        override val diagnosticDetail: String get() = rawError
    }

    data class NoNetwork(
        val rawError: String = "IOException / Network",
        override val attempts: List<AttemptDiagnostic> = emptyList(),
    ) : TimetableResult("لا يوجد اتصال بالإنترنت. لم يُخصم من رصيدك.") {
        override val diagnosticDetail: String get() = rawError
    }

    data class Timeout(
        val rawError: String = "Timed out after ${GeminiClient.REQUEST_TIMEOUT_MS / 1000}s",
        override val attempts: List<AttemptDiagnostic> = emptyList(),
    ) : TimetableResult("انتهت مهلة قراءة الجدول (60 ثانية). لم يُخصم من رصيدك، حاول مجدداً.") {
        override val diagnosticDetail: String get() = rawError
    }

    data class ResponseTooLarge(
        val rawError: String = "Response exceeded ${GeminiClient.MAX_RESPONSE_BYTES} bytes",
        override val attempts: List<AttemptDiagnostic> = emptyList(),
    ) : TimetableResult("استجابة الخدمة كبيرة جداً.") {
        override val diagnosticDetail: String get() = rawError
    }

    data class Unexpected(
        val userFriendlyDetail: String,
        override val diagnosticDetail: String? = null,
        val isAppCheckFailure: Boolean = false,
        override val attempts: List<AttemptDiagnostic> = emptyList(),
    ) : TimetableResult(userFriendlyDetail)
}

internal fun interface StreamAttemptRunner {
    suspend fun stream(
        modelName: String,
        mode: AttemptMode,
        timeoutMs: Long,
        payload: Content,
    ): String
}

class GeminiClient internal constructor(
    private val streamRunner: StreamAttemptRunner? = null,
) {
    constructor() : this(null)

    private val timeEntrySchema by lazy {
        Schema.obj(
            properties = mapOf(
                "time" to Schema.string(description = "Time in HH:mm format or null if unreadable", nullable = true),
                "period" to Schema.string(description = "AM or PM or null if unreadable", nullable = true),
            ),
            optionalProperties = listOf("time", "period"),
        )
    }

    private val daySchema by lazy {
        Schema.obj(
            properties = mapOf(
                "gregorianDate" to Schema.string(description = "Gregorian date in yyyy-MM-dd format"),
                "hijriDay" to Schema.integer(description = "Hijri day number", nullable = true),
                "fajr" to timeEntrySchema,
                "dhuhr" to timeEntrySchema,
                "asr" to timeEntrySchema,
                "maghrib" to timeEntrySchema,
                "isha" to timeEntrySchema,
            ),
            optionalProperties = listOf("hijriDay"),
        )
    }

    private val flagSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "gregorianDate" to Schema.string(description = "Gregorian date yyyy-MM-dd"),
                "field" to Schema.string(description = "Prayer field name"),
                "reason" to Schema.string(description = "Short Arabic reason"),
            ),
        )
    }

    private val timetableSchema by lazy {
        Schema.obj(
            properties = mapOf(
                "error" to Schema.string(
                    description = "Set to not_a_prayer_timetable ONLY if image is not a prayer timetable, otherwise null",
                    nullable = true,
                ),
                "hijriMonthName" to Schema.string(nullable = true),
                "hijriYear" to Schema.integer(nullable = true),
                "fajrNoteMinutes" to Schema.integer(nullable = true),
                "days" to Schema.array(
                    items = daySchema,
                    description = "All daily rows extracted from the prayer timetable (required, empty only when error=not_a_prayer_timetable)",
                ),
                "flags" to Schema.array(
                    items = flagSchema,
                    description = "Optional review warnings for unreadable cells; empty array if none",
                ),
            ),
            optionalProperties = listOf("error", "hijriMonthName", "hijriYear", "fajrNoteMinutes"),
        )
    }

    private val primarySchemaModel by lazy { createSchemaModel(ModelConfig.MODEL_NAME) }
    private val fallbackSchemaModel by lazy { createSchemaModel(ModelConfig.FALLBACK_MODEL_NAME) }
    private val fallbackPlainJsonModel by lazy { createPlainJsonModel(ModelConfig.FALLBACK_MODEL_NAME) }
    private val primaryProbeModel by lazy { createProbeModel(ModelConfig.MODEL_NAME) }
    private val fallbackProbeModel by lazy { createProbeModel(ModelConfig.FALLBACK_MODEL_NAME) }

    private fun createSchemaModel(modelName: String): GenerativeModel = Firebase.ai.generativeModel(
        modelName = modelName,
        generationConfig = generationConfig {
            temperature = 0f
            responseMimeType = "application/json"
            responseSchema = timetableSchema
        },
        systemInstruction = content {
            text(ExtractionPrompt.SYSTEM_INSTRUCTION)
        },
    )

    private fun createPlainJsonModel(modelName: String): GenerativeModel = Firebase.ai.generativeModel(
        modelName = modelName,
        generationConfig = generationConfig {
            temperature = 0f
            responseMimeType = "application/json"
        },
        systemInstruction = content {
            text(ExtractionPrompt.PLAIN_JSON_INSTRUCTION)
        },
    )

    private fun createProbeModel(modelName: String): GenerativeModel = Firebase.ai.generativeModel(
        modelName = modelName,
        generationConfig = generationConfig {
            temperature = 0f
            maxOutputTokens = 16
        },
    )

    private fun modelFor(modelName: String, mode: AttemptMode): GenerativeModel = when (mode) {
        AttemptMode.SCHEMA -> if (modelName == ModelConfig.MODEL_NAME) primarySchemaModel else fallbackSchemaModel
        AttemptMode.PLAIN_JSON -> fallbackPlainJsonModel
        AttemptMode.TEXT_PING -> if (modelName == ModelConfig.MODEL_NAME) primaryProbeModel else fallbackProbeModel
    }

    suspend fun probeServer(
        onProgress: (AttemptDiagnostic) -> Unit = {},
    ): ServerProbeResult {
        val models = listOf(ModelConfig.MODEL_NAME, ModelConfig.FALLBACK_MODEL_NAME)
        val results = mutableListOf<AttemptDiagnostic>()
        val probeContent = content { text("Reply with OK") }

        for (modelName in models) {
            val startedAt = System.currentTimeMillis()
            val diagnostic = try {
                val text = executeStream(modelName, AttemptMode.TEXT_PING, PROBE_TIMEOUT_MS, probeContent)
                val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
                if (text.isBlank()) {
                    AttemptDiagnostic(
                        modelName = modelName,
                        mode = AttemptMode.TEXT_PING,
                        elapsedMillis = elapsed,
                        succeeded = false,
                        rawDetail = "استجابة نصية فارغة خلال ${elapsed}ms",
                    )
                } else {
                    val preview = text.replace('\n', ' ').trim().take(48)
                    AttemptDiagnostic(
                        modelName = modelName,
                        mode = AttemptMode.TEXT_PING,
                        elapsedMillis = elapsed,
                        succeeded = true,
                        rawDetail = "متصل · ردّ \"$preview\" خلال ${elapsed}ms",
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
                val mapped = mapException(modelName, AttemptMode.TEXT_PING, PROBE_TIMEOUT_MS, e)
                AttemptDiagnostic(
                    modelName = modelName,
                    mode = AttemptMode.TEXT_PING,
                    elapsedMillis = elapsed,
                    succeeded = false,
                    rawDetail = mapped.diagnosticDetail ?: extractRawError(e),
                )
            }
            results += diagnostic
            onProgress(diagnostic)
        }

        return ServerProbeResult(
            checkedAtMillis = System.currentTimeMillis(),
            appCheckLabel = AppCheckStatus.current.labelAr,
            attempts = results.toList(),
        )
    }

    suspend fun extractTimetable(
        imageJpeg: ByteArray,
        onProgress: (AttemptProgress) -> Unit = {},
    ): TimetableResult {
        if (imageJpeg.isEmpty() || imageJpeg.size > MAX_INLINE_IMAGE_BYTES) {
            return TimetableResult.Unexpected(
                userFriendlyDetail = "حجم الصورة غير مناسب للرفع. جرّب صورة أوضح.",
                diagnosticDetail = "حجم الصورة غير صالح (${imageJpeg.size} بايت)",
            )
        }

        val attempts = mutableListOf<AttemptDiagnostic>()

        // 1. Primary model with strict Schema
        onProgress(
            AttemptProgress(
                activeModelName = ModelConfig.MODEL_NAME,
                activeMode = AttemptMode.SCHEMA,
                isFallback = false,
                reasonAr = null,
                completedAttempts = emptyList(),
            ),
        )
        val primaryOutcome = runAttempt(ModelConfig.MODEL_NAME, AttemptMode.SCHEMA, imageJpeg)
        attempts += primaryOutcome.diagnostic
        val primaryResult = primaryOutcome.result

        if (primaryResult is TimetableResult.Success && hasUsablePayload(primaryResult.rawJson)) {
            return primaryResult.copy(attempts = attempts.toList())
        }
        if (isTerminalClientFailure(primaryResult)) {
            return attachAttempts(primaryResult, attempts)
        }

        // 2. Fallback model with strict Schema
        val primaryReason = primaryOutcome.diagnostic.rawDetail
        val switchReason = "تعذّر ${ModelConfig.MODEL_NAME} ($primaryReason) — جارٍ التحويل إلى ${ModelConfig.FALLBACK_MODEL_NAME}…"
        logWarn("Falling back to ${ModelConfig.FALLBACK_MODEL_NAME} (Schema): $primaryReason")
        onProgress(
            AttemptProgress(
                activeModelName = ModelConfig.FALLBACK_MODEL_NAME,
                activeMode = AttemptMode.SCHEMA,
                isFallback = true,
                reasonAr = switchReason,
                completedAttempts = attempts.toList(),
            ),
        )

        val fallbackSchemaOutcome = runAttempt(ModelConfig.FALLBACK_MODEL_NAME, AttemptMode.SCHEMA, imageJpeg)
        attempts += fallbackSchemaOutcome.diagnostic
        val fallbackSchemaResult = fallbackSchemaOutcome.result

        if (fallbackSchemaResult is TimetableResult.Success && hasUsablePayload(fallbackSchemaResult.rawJson)) {
            return fallbackSchemaResult.copy(attempts = attempts.toList())
        }
        if (isTerminalClientFailure(fallbackSchemaResult)) {
            return attachAttempts(fallbackSchemaResult, attempts)
        }

        // 3. Fallback model with Plain JSON (without responseSchema)
        val schemaReason = if (fallbackSchemaResult is TimetableResult.Success) {
            "ردّ ناقص/غير مكتمل بالـ Schema (${fallbackSchemaResult.rawJson.length} حرفاً)"
        } else {
            fallbackSchemaOutcome.diagnostic.rawDetail
        }
        val plainJsonReason = "تعذّر مسار Schema ($schemaReason) — تجربة JSON نصي مرن على ${ModelConfig.FALLBACK_MODEL_NAME}…"
        logWarn("Trying schema-less JSON fallback on ${ModelConfig.FALLBACK_MODEL_NAME}: $schemaReason")
        onProgress(
            AttemptProgress(
                activeModelName = ModelConfig.FALLBACK_MODEL_NAME,
                activeMode = AttemptMode.PLAIN_JSON,
                isFallback = true,
                reasonAr = plainJsonReason,
                completedAttempts = attempts.toList(),
            ),
        )

        val plainJsonOutcome = runAttempt(ModelConfig.FALLBACK_MODEL_NAME, AttemptMode.PLAIN_JSON, imageJpeg)
        attempts += plainJsonOutcome.diagnostic
        val plainJsonResult = plainJsonOutcome.result

        if (plainJsonResult is TimetableResult.Success) {
            return plainJsonResult.copy(attempts = attempts.toList())
        }
        if (fallbackSchemaResult is TimetableResult.Success) {
            // Preserve partial JSON received in step 2 so CapturePipeline can show exact parse error + preview.
            return fallbackSchemaResult.copy(attempts = attempts.toList())
        }
        return attachAttempts(plainJsonResult, attempts)
    }

    private fun hasUsablePayload(rawJson: String): Boolean {
        val parsed = ExtractionParser.parse(rawJson)
        return parsed.isSuccess || parsed.exceptionOrNull() is ExtractionFailure.NotAPrayerTimetable
    }

    private fun isTerminalClientFailure(result: TimetableResult): Boolean = when (result) {
        is TimetableResult.NoNetwork -> true
        is TimetableResult.Unexpected -> result.isAppCheckFailure
        else -> false
    }

    private fun attachAttempts(result: TimetableResult, attempts: List<AttemptDiagnostic>): TimetableResult {
        val list = attempts.toList()
        return when (result) {
            is TimetableResult.Success -> result.copy(attempts = list)
            is TimetableResult.QuotaExceeded -> result.copy(attempts = list)
            is TimetableResult.ServiceUnavailable -> result.copy(attempts = list)
            is TimetableResult.NoNetwork -> result.copy(attempts = list)
            is TimetableResult.Timeout -> result.copy(attempts = list)
            is TimetableResult.ResponseTooLarge -> result.copy(attempts = list)
            is TimetableResult.Unexpected -> result.copy(attempts = list)
        }
    }

    private data class AttemptExecution(
        val result: TimetableResult,
        val diagnostic: AttemptDiagnostic,
    )

    private class ResponseLimitExceededException : Exception()

    private suspend fun executeStream(
        modelName: String,
        mode: AttemptMode,
        timeoutMs: Long,
        payload: Content,
    ): String {
        streamRunner?.let { runner ->
            return runner.stream(modelName, mode, timeoutMs, payload)
        }
        val generativeModel = modelFor(modelName, mode)
        val accumulated = StringBuilder()
        withTimeout(timeoutMs) {
            generativeModel.generateContentStream(payload).collect { chunk ->
                val piece = chunk.text.orEmpty()
                if (piece.isNotEmpty()) {
                    accumulated.append(piece)
                    if (accumulated.length > MAX_RESPONSE_BYTES) {
                        throw ResponseLimitExceededException()
                    }
                }
            }
        }
        return accumulated.toString()
    }

    private suspend fun runAttempt(
        modelName: String,
        mode: AttemptMode,
        imageJpeg: ByteArray,
    ): AttemptExecution {
        val startedAt = System.currentTimeMillis()
        val promptText = if (mode == AttemptMode.PLAIN_JSON) {
            "Extract all daily rows from this Arabic Islamic prayer timetable and return ONLY a single raw JSON object matching the requested fields."
        } else {
            "Extract all daily rows from this Arabic Islamic prayer timetable into the required `days` array in JSON."
        }
        val payload = content {
            inlineData(imageJpeg, "image/jpeg")
            text(promptText)
        }
        return try {
            val text = executeStream(modelName, mode, REQUEST_TIMEOUT_MS, payload)
            val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
            if (text.toByteArray(Charsets.UTF_8).size > MAX_RESPONSE_BYTES) {
                val res = TimetableResult.ResponseTooLarge("تجاوز الرد الحد المسموح (${text.length} حرفاً)")
                AttemptExecution(
                    result = res,
                    diagnostic = AttemptDiagnostic(modelName, mode, elapsed, false, res.rawError),
                )
            } else if (text.isBlank()) {
                val detail = "استجابة فارغة (0 أحرف) بعد ${elapsed}ms"
                AttemptExecution(
                    result = TimetableResult.Unexpected(
                        userFriendlyDetail = "لم ترجع الخدمة بيانات للجدول. أعد التصوير بوضوح.",
                        diagnosticDetail = detail,
                    ),
                    diagnostic = AttemptDiagnostic(modelName, mode, elapsed, false, detail),
                )
            } else {
                if (BuildConfig.DEBUG) {
                    logDebug("Timetable extracted via $modelName (${mode.labelAr}, ${text.length} chars in ${elapsed}ms)")
                }
                val parsedOk = hasUsablePayload(text)
                AttemptExecution(
                    result = TimetableResult.Success(rawJson = text, modelUsed = modelName, modeUsed = mode),
                    diagnostic = AttemptDiagnostic(
                        modelName = modelName,
                        mode = mode,
                        elapsedMillis = elapsed,
                        succeeded = parsedOk,
                        rawDetail = if (parsedOk) {
                            "استلم ${text.length} حرفاً خلال ${elapsed}ms"
                        } else {
                            "استلم ${text.length} حرفاً خلال ${elapsed}ms (JSON ناقص/غير مطابق)"
                        },
                    ),
                )
            }
        } catch (e: CancellationException) {
            if (e is TimeoutCancellationException) {
                val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
                logWarn("$modelName (${mode.labelAr}) timed out after ${REQUEST_TIMEOUT_MS}ms")
                val raw = "TimeoutCancellationException after ${elapsed}ms (limit ${REQUEST_TIMEOUT_MS / 1000}s)"
                AttemptExecution(
                    result = TimetableResult.Timeout(rawError = raw),
                    diagnostic = AttemptDiagnostic(modelName, mode, elapsed, false, raw),
                )
            } else {
                throw e
            }
        } catch (e: Exception) {
            val elapsed = (System.currentTimeMillis() - startedAt).coerceAtLeast(1L)
            val mapped = mapException(modelName, mode, REQUEST_TIMEOUT_MS, e)
            AttemptExecution(
                result = mapped,
                diagnostic = AttemptDiagnostic(
                    modelName = modelName,
                    mode = mode,
                    elapsedMillis = elapsed,
                    succeeded = false,
                    rawDetail = mapped.diagnosticDetail ?: extractRawError(e),
                ),
            )
        }
    }

    private fun mapException(
        modelName: String,
        mode: AttemptMode,
        timeoutMs: Long,
        e: Exception,
    ): TimetableResult = when (e) {
        is ResponseLimitExceededException -> {
            TimetableResult.ResponseTooLarge("تجاوز الرد ${MAX_RESPONSE_BYTES / 1024} KB")
        }
        is TimeoutCancellationException -> {
            logWarn("$modelName (${mode.labelAr}) timed out after ${timeoutMs}ms")
            TimetableResult.Timeout("TimeoutCancellationException (limit ${timeoutMs / 1000}s)")
        }
        is FirebaseAIException -> {
            logError("$modelName (${mode.labelAr}) Firebase AI failure", e)
            mapFirebaseException(e)
        }
        is IOException -> {
            val raw = extractRawError(e)
            logError("$modelName (${mode.labelAr}) network failure: $raw", e)
            TimetableResult.NoNetwork(raw)
        }
        else -> {
            val raw = extractRawError(e)
            logError("$modelName (${mode.labelAr}) unexpected failure: $raw", e)
            val lower = raw.lowercase()
            when {
                isTransientOverloadMessage(lower) -> TimetableResult.ServiceUnavailable(raw)
                "network" in lower || "unable to resolve host" in lower || "connect" in lower ->
                    TimetableResult.NoNetwork(raw)
                "quota" in lower || "429" in lower || "resource_exhausted" in lower ->
                    TimetableResult.QuotaExceeded(raw)
                "timeout" in lower || "timed out" in lower || "deadline" in lower ->
                    TimetableResult.Timeout(raw)
                else -> TimetableResult.Unexpected(
                    userFriendlyDetail = e.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: "تعذّرت قراءة الجدول. حاول مجدداً.",
                    diagnosticDetail = raw,
                )
            }
        }
    }

    private fun logDebug(message: String) {
        try {
            Log.d(TAG, message)
        } catch (_: RuntimeException) {
            // Unmocked android.util.Log in pure JVM unit tests.
        }
    }

    private fun logWarn(message: String) {
        try {
            Log.w(TAG, message)
        } catch (_: RuntimeException) {
            // Unmocked android.util.Log in pure JVM unit tests.
        }
    }

    private fun logError(message: String, throwable: Throwable) {
        try {
            Log.e(TAG, message, throwable)
        } catch (_: RuntimeException) {
            // Unmocked android.util.Log in pure JVM unit tests.
        }
    }

    private fun extractRawError(e: Throwable): String {
        val cls = e.javaClass.simpleName
        val msg = (e.localizedMessage ?: e.message).orEmpty().replace('\n', ' ').trim()
        val causeMsg = e.cause?.let { cause ->
            val cMsg = (cause.localizedMessage ?: cause.message).orEmpty().replace('\n', ' ').trim()
            if (cMsg.isNotBlank() && cMsg != msg) " | cause=${cause.javaClass.simpleName}: $cMsg" else ""
        }.orEmpty()
        val combined = if (msg.isNotBlank()) "$cls: $msg$causeMsg" else "$cls$causeMsg"
        return combined.take(280)
    }

    private fun isTransientOverloadMessage(lower: String): Boolean =
        "high demand" in lower ||
            "spikes in demand" in lower ||
            "overloaded" in lower ||
            "try again later" in lower ||
            "service unavailable" in lower ||
            "503" in lower ||
            "500" in lower ||
            "unavailable" in lower ||
            "busy" in lower

    private fun mapFirebaseException(e: FirebaseAIException): TimetableResult {
        val raw = extractRawError(e)
        val lower = raw.lowercase()
        return when {
            "403" in lower || "app check" in lower || "attestation" in lower || "permission_denied" in lower ->
                TimetableResult.Unexpected(
                    userFriendlyDetail = "تعذّر التحقق من صلاحية التطبيق (App Check). تأكد من تحديث التطبيق واتصال الإنترنت.",
                    diagnosticDetail = raw,
                    isAppCheckFailure = true,
                )
            "429" in lower || "quota" in lower || "resource_exhausted" in lower ->
                TimetableResult.QuotaExceeded(raw)
            "network" in lower || "unable to resolve host" in lower || "failed to connect" in lower ->
                TimetableResult.NoNetwork(raw)
            "timeout" in lower || "timed out" in lower || "deadline_exceeded" in lower ->
                TimetableResult.Timeout(raw)
            isTransientOverloadMessage(lower) ->
                TimetableResult.ServiceUnavailable(raw)
            else -> TimetableResult.Unexpected(
                userFriendlyDetail = e.localizedMessage?.takeIf { it.isNotBlank() } ?: "تعذّرت قراءة الجدول من الخدمة.",
                diagnosticDetail = raw,
            )
        }
    }

    companion object {
        private const val TAG = "GeminiClient"
        const val REQUEST_TIMEOUT_MS = 60_000L
        const val PROBE_TIMEOUT_MS = 15_000L
        const val MAX_RESPONSE_BYTES = 512 * 1024
        const val MAX_INLINE_IMAGE_BYTES = 20 * 1024 * 1024
    }
}
