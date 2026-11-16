package com.adhanreminder.app.data

import android.util.Log
import com.adhanreminder.app.BuildConfig
import com.adhanreminder.app.domain.ExtractionPrompt
import com.google.firebase.Firebase
import com.google.firebase.ai.ai
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
}

sealed class TimetableResult(open val userMessage: String) {
    open val diagnosticDetail: String? get() = null

    data class Success(val rawJson: String) : TimetableResult("تمت قراءة الجدول.")
    data object QuotaExceeded : TimetableResult("تجاوزت الحد المتاح مؤقتاً. حاول لاحقاً.") {
        override val diagnosticDetail: String get() = "تجاوز الحصة أو حد الطلبات (429 / Quota)"
    }
    data object ServiceUnavailable : TimetableResult("الخدمة غير متاحة الآن. حاول بعد قليل.") {
        override val diagnosticDetail: String get() = "الخدمة غير متاحة مؤقتاً (500 / 503)"
    }
    data object NoNetwork : TimetableResult("لا يوجد اتصال بالإنترنت.") {
        override val diagnosticDetail: String get() = "فشل الاتصال بالشبكة (IOException / Network)"
    }
    data object Timeout : TimetableResult("انتهت المهلة (45 ثانية). حاول مجدداً.") {
        override val diagnosticDetail: String get() = "انتهت مهلة 45 ثانية قبل استلام رد من النموذج"
    }
    data object ResponseTooLarge : TimetableResult("استجابة الخدمة كبيرة جداً.") {
        override val diagnosticDetail: String get() = "حجم الرد أكبر من الحد المسموح"
    }
    data class Unexpected(
        val userFriendlyDetail: String,
        override val diagnosticDetail: String? = null,
    ) : TimetableResult(userFriendlyDetail)
}

class GeminiClient {

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
                    description = "Set to not_a_prayer_timetable if image is not a prayer timetable",
                    nullable = true,
                ),
                "hijriMonthName" to Schema.string(nullable = true),
                "hijriYear" to Schema.integer(nullable = true),
                "fajrNoteMinutes" to Schema.integer(nullable = true),
                "days" to Schema.array(items = daySchema),
                "flags" to Schema.array(items = flagSchema),
            ),
            optionalProperties = listOf("error", "hijriMonthName", "hijriYear", "fajrNoteMinutes", "days", "flags"),
        )
    }

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = ModelConfig.MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0f
                responseMimeType = "application/json"
                responseSchema = timetableSchema
            },
            systemInstruction = content {
                text(ExtractionPrompt.SYSTEM_INSTRUCTION)
            },
        )
    }

    suspend fun extractTimetable(imageJpeg: ByteArray): TimetableResult {
        if (imageJpeg.isEmpty() || imageJpeg.size > MAX_INLINE_IMAGE_BYTES) {
            return TimetableResult.Unexpected(
                userFriendlyDetail = "حجم الصورة غير مناسب للرفع. جرّب صورة أوضح.",
                diagnosticDetail = "حجم الصورة غير صالح (${imageJpeg.size} بايت)",
            )
        }

        val startedAt = System.currentTimeMillis()
        return try {
            val response = withTimeout(REQUEST_TIMEOUT_MS) {
                model.generateContent(
                    content {
                        inlineData(imageJpeg, "image/jpeg")
                        text("Extract the monthly Islamic prayer timetable JSON from this image.")
                    }
                )
            }
            val text = response.text.orEmpty()
            val elapsed = System.currentTimeMillis() - startedAt
            if (text.isBlank()) {
                TimetableResult.Unexpected(
                    userFriendlyDetail = "لم ترجع الخدمة بيانات للجدول. أعد التصوير بوضوح.",
                    diagnosticDetail = "استجابة فارغة من النموذج بعد ${elapsed}ms",
                )
            } else if (text.toByteArray(Charsets.UTF_8).size > MAX_RESPONSE_BYTES) {
                TimetableResult.ResponseTooLarge
            } else {
                if (BuildConfig.DEBUG) {
                    Log.d(TAG, "Timetable extracted (${text.length} chars in ${elapsed}ms)")
                }
                TimetableResult.Success(text)
            }
        } catch (e: TimeoutCancellationException) {
            Log.e(TAG, "Gemini timetable extraction timed out after ${REQUEST_TIMEOUT_MS}ms", e)
            TimetableResult.Timeout
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAIException) {
            Log.e(TAG, "Firebase AI extraction failed", e)
            mapFirebaseException(e)
        } catch (e: IOException) {
            Log.e(TAG, "Network failure during timetable extraction", e)
            TimetableResult.NoNetwork
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected extraction failure", e)
            val rawDetail = "${e.javaClass.simpleName}: ${e.localizedMessage.orEmpty()}".take(160)
            val msg = e.localizedMessage?.lowercase().orEmpty()
            when {
                "network" in msg || "unable to resolve host" in msg || "connect" in msg -> TimetableResult.NoNetwork
                "quota" in msg || "429" in msg -> TimetableResult.QuotaExceeded
                else -> TimetableResult.Unexpected(
                    userFriendlyDetail = e.localizedMessage?.takeIf { it.isNotBlank() }
                        ?: "تعذّرت قراءة الجدول. حاول مجدداً.",
                    diagnosticDetail = rawDetail,
                )
            }
        }
    }

    private fun mapFirebaseException(e: FirebaseAIException): TimetableResult {
        val message = e.localizedMessage.orEmpty()
        val lower = message.lowercase()
        val summary = "FirebaseAI: ${message.ifBlank { e.javaClass.simpleName }}".take(160)
        return when {
            "429" in lower || "quota" in lower || "resource_exhausted" in lower ->
                TimetableResult.QuotaExceeded
            "network" in lower || "unavailable" in lower || "timeout" in lower ->
                TimetableResult.NoNetwork
            "403" in lower || "app check" in lower || "attestation" in lower ->
                TimetableResult.Unexpected(
                    userFriendlyDetail = "تعذّر التحقق من صلاحية التطبيق (App Check). تأكد من تحديث التطبيق واتصال الإنترنت.",
                    diagnosticDetail = "App Check / 403: ${message.take(140)}",
                )
            "503" in lower || "500" in lower || "busy" in lower ->
                TimetableResult.ServiceUnavailable
            else -> TimetableResult.Unexpected(
                userFriendlyDetail = message.takeIf { it.isNotBlank() } ?: "تعذّرت قراءة الجدول من الخدمة.",
                diagnosticDetail = summary,
            )
        }
    }

    companion object {
        private const val TAG = "GeminiClient"
        const val REQUEST_TIMEOUT_MS = 45_000L
        const val MAX_RESPONSE_BYTES = 512 * 1024
        const val MAX_INLINE_IMAGE_BYTES = 20 * 1024 * 1024
    }
}
