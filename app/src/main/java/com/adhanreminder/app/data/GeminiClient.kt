package com.adhanreminder.app.data

import android.util.Base64
import com.adhanreminder.app.BuildConfig
import com.adhanreminder.app.domain.ExtractionPrompt
import com.adhanreminder.app.domain.FailureReason
import com.adhanreminder.app.domain.GeminiErrorMapping
import com.adhanreminder.app.domain.GeminiErrorParser
import com.adhanreminder.app.domain.LocalKeyCheck
import com.adhanreminder.app.domain.inspectApiKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

object ModelConfig {
    // Gemini 2.5 Flash accepts image input; verify access for the configured project before release.
    const val MODEL_NAME = "gemini-2.5-flash"
}

sealed class TimetableResult(open val userMessage: String) {
    data class Success(val rawJson: String) : TimetableResult("تمت قراءة الجدول.")
    data object InvalidKey : TimetableResult("المفتاح مرفوض. تحقق من نسخه كاملاً.")
    data object QuotaExceeded : TimetableResult("تجاوزت الحد المجاني. حاول لاحقاً.")
    data object ServiceUnavailable : TimetableResult("الخدمة غير متاحة الآن.")
    data object NoNetwork : TimetableResult("لا يوجد اتصال بالإنترنت.")
    data object ResponseTooLarge : TimetableResult("استجابة الخدمة كبيرة جداً.")
    data class Unexpected(val errorCode: String) : TimetableResult("حدث خطأ غير متوقع.")
}

object GeminiRequestBuilder {
    fun buildImageRequest(imageJpeg: ByteArray): String {
        val inlineData = JSONObject()
            .put("mime_type", "image/jpeg")
            .put("data", Base64.encodeToString(imageJpeg, Base64.NO_WRAP))
        val parts = JSONArray().put(JSONObject().put("inline_data", inlineData))
        return JSONObject()
            .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", ExtractionPrompt.SYSTEM_INSTRUCTION))))
            .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", parts)))
            .put("generationConfig", JSONObject().put("temperature", 0).put("responseMimeType", "application/json").put("responseSchema", JSONObject(ExtractionPrompt.RESPONSE_SCHEMA)))
            .toString()
    }

    fun buildTextRequest(text: String): String = JSONObject()
        .put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", text)))))
        .put("generationConfig", JSONObject().put("temperature", 0).put("maxOutputTokens", 5))
        .toString()
}

class GeminiClient(private val settings: SettingsRepository) {
    suspend fun extractTimetable(imageJpeg: ByteArray): TimetableResult =
        request(GeminiRequestBuilder.buildImageRequest(imageJpeg))

    suspend fun testKey(key: String): TimetableResult {
        val inspected = inspectApiKey(key)
        if (inspected.error != null || inspected.value.length > SettingsRepository.MAX_API_KEY_LENGTH) return TimetableResult.InvalidKey
        return request(GeminiRequestBuilder.buildTextRequest("Reply with OK"), inspected.value)
    }

    private suspend fun request(body: String, explicitKey: String? = null): TimetableResult = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val key = (explicitKey ?: settings.geminiApiKey.first()).trim()
            if (key.isBlank() || key.length > SettingsRepository.MAX_API_KEY_LENGTH) return@withContext TimetableResult.InvalidKey
            connection = (URL("https://generativelanguage.googleapis.com/v1beta/models/${ModelConfig.MODEL_NAME}:generateContent").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 60_000
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("x-goog-api-key", key)
            }
            connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use(::readCapped).orEmpty()
            when {
                code !in 200..299 -> when (val mapped = GeminiErrorParser.map(GeminiErrorParser.parse(code, response))) {
                    is GeminiErrorMapping.Warning -> TimetableResult.QuotaExceeded
                    is GeminiErrorMapping.Failure -> when (mapped.reason) {
                        FailureReason.KEY_INVALID, FailureReason.KEY_EXPIRED, FailureReason.KEY_RESTRICTED, FailureReason.KEY_NO_PERMISSION, FailureReason.SERVICE_DISABLED -> TimetableResult.InvalidKey
                        FailureReason.NO_NETWORK, FailureReason.TIMEOUT -> TimetableResult.NoNetwork
                        FailureReason.SERVICE_BUSY -> TimetableResult.ServiceUnavailable
                        else -> TimetableResult.Unexpected(mapped.reason.code.uppercase())
                    }
                }
                else -> {
                    val text = JSONObject(response).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")?.optJSONObject(0)?.optString("text").orEmpty()
                    if (BuildConfig.DEBUG) android.util.Log.d("GeminiClient", "response received (${text.length} chars)")
                    TimetableResult.Success(text)
                }
            }
        } catch (_: ResponseTooLargeException) {
            TimetableResult.ResponseTooLarge
        } catch (_: SocketTimeoutException) {
            TimetableResult.NoNetwork
        } catch (_: IOException) {
            TimetableResult.NoNetwork
        } catch (error: Exception) {
            if (BuildConfig.DEBUG) android.util.Log.d("GeminiClient", "request error ${error::class.simpleName}")
            TimetableResult.Unexpected(error::class.simpleName ?: "Exception")
        } finally {
            connection?.disconnect()
        }
    }

    private fun readCapped(input: InputStream): String {
        val buffer = ByteArray(16 * 1024)
        val output = ByteArrayOutputStream()
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_RESPONSE_BYTES) throw ResponseTooLargeException()
            output.write(buffer, 0, read)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private class ResponseTooLargeException : Exception()

    companion object { const val MAX_RESPONSE_BYTES = 512 * 1024 }
}
