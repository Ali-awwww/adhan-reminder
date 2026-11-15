package com.adhanreminder.app.data

import com.adhanreminder.app.domain.FailureReason
import com.adhanreminder.app.domain.GeminiErrorMapping
import com.adhanreminder.app.domain.GeminiErrorParser
import com.adhanreminder.app.domain.KeyCheckResult
import com.adhanreminder.app.domain.KeyFormat
import com.adhanreminder.app.domain.LocalKeyCheck
import com.adhanreminder.app.domain.OkReason
import com.adhanreminder.app.domain.WarningReason
import com.adhanreminder.app.domain.VerificationStep
import com.adhanreminder.app.domain.inspectApiKey
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL

sealed class VerificationProgress {
    data class Running(val step: VerificationStep) : VerificationProgress()
    data class Done(val step: VerificationStep) : VerificationProgress()
    data class Failed(val step: VerificationStep, val result: KeyCheckResult.Failed) : VerificationProgress()
}

class ApiKeyVerifier(private val modelName: String = ModelConfig.MODEL_NAME) {
    fun verify(key: String): Flow<VerificationProgress> = flow {
        val inspected = inspectApiKey(key)
        val localFailure = when (inspected.error) {
            LocalKeyCheck.EMPTY -> FailureReason.EMPTY
            LocalKeyCheck.INTERNAL_WHITESPACE -> FailureReason.INTERNAL_WHITESPACE
            null -> null
        }
        if (localFailure != null) {
            emit(VerificationProgress.Failed(VerificationStep.ACCESS, KeyCheckResult.Failed(localFailure, VerificationStep.ACCESS)))
            return@flow
        }
        val format = inspected.format
        emit(VerificationProgress.Running(VerificationStep.ACCESS))
        val access = request("GET", "models", inspected.value, null)
        if (access !is HttpResult.Success) {
            emitFailure(VerificationStep.ACCESS, access, format); return@flow
        }
        emit(VerificationProgress.Done(VerificationStep.ACCESS))
        val models = parseModels(access.body)
        val service = models.firstOrNull { it.name == modelName || it.name == "models/$modelName" || it.name.removePrefix("models/") == modelName }
        emit(VerificationProgress.Running(VerificationStep.SERVICE))
        if (service == null) {
            emit(VerificationProgress.Failed(VerificationStep.SERVICE, KeyCheckResult.Failed(FailureReason.MODEL_NOT_FOUND, VerificationStep.SERVICE)))
            return@flow
        }
        if (!service.methods.contains("generateContent")) {
            emit(VerificationProgress.Failed(VerificationStep.SERVICE, KeyCheckResult.Failed(FailureReason.MODEL_NO_IMAGE_SUPPORT, VerificationStep.SERVICE)))
            return@flow
        }
        emit(VerificationProgress.Done(VerificationStep.SERVICE))
        emit(VerificationProgress.Running(VerificationStep.GENERATE))
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", "OK"))))).put("generationConfig", JSONObject().put("temperature", 0).put("maxOutputTokens", 1)).toString()
        val generated = request("POST", "models/${modelName}:generateContent", inspected.value, body)
        when (generated) {
            is HttpResult.Success -> emit(VerificationProgress.Done(VerificationStep.GENERATE))
            else -> emitFailure(VerificationStep.GENERATE, generated, format)
        }
        if (generated is HttpResult.Success) {
            // UI turns this terminal Done into OK_READY using the same checked key and model.
        }
    }.flowOn(Dispatchers.IO)

    suspend fun verifyOnce(key: String): KeyCheckResult {
        val inspected = inspectApiKey(key)
        val local = when (inspected.error) { LocalKeyCheck.EMPTY -> FailureReason.EMPTY; LocalKeyCheck.INTERNAL_WHITESPACE -> FailureReason.INTERNAL_WHITESPACE; null -> null }
        if (local != null) return KeyCheckResult.Failed(local, VerificationStep.ACCESS)
        val access = request("GET", "models", inspected.value, null)
        if (access !is HttpResult.Success) return failure(access, VerificationStep.ACCESS)
        val service = parseModels(access.body).firstOrNull { it.name.removePrefix("models/") == modelName }
            ?: return KeyCheckResult.Failed(FailureReason.MODEL_NOT_FOUND, VerificationStep.SERVICE)
        if (!service.methods.contains("generateContent")) return KeyCheckResult.Failed(FailureReason.MODEL_NO_IMAGE_SUPPORT, VerificationStep.SERVICE)
        val body = JSONObject().put("contents", JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", "OK"))))).put("generationConfig", JSONObject().put("temperature", 0).put("maxOutputTokens", 1)).toString()
        val generated = request("POST", "models/${modelName}:generateContent", inspected.value, body)
        return when (generated) {
            is HttpResult.Success -> KeyCheckResult.Ok(OkReason.OK_READY, inspected.format, modelName, System.currentTimeMillis())
            is HttpResult.HttpError -> if (generated.code == 429) KeyCheckResult.Warning(WarningReason.QUOTA_LIMITED, inspected.format, modelName, System.currentTimeMillis()) else failure(generated, VerificationStep.GENERATE)
            is HttpResult.NetworkError -> failure(generated, VerificationStep.GENERATE)
        }
    }

    private suspend fun kotlinx.coroutines.flow.FlowCollector<VerificationProgress>.emitFailure(step: VerificationStep, result: HttpResult, format: KeyFormat) {
        emit(VerificationProgress.Failed(step, failure(result, step)))
    }

    private fun failure(result: HttpResult, step: VerificationStep): KeyCheckResult.Failed = when (result) {
        is HttpResult.NetworkError -> KeyCheckResult.Failed(result.reason, step)
        is HttpResult.HttpError -> {
            val parsed = GeminiErrorParser.parse(result.code, result.body)
            val mapped = GeminiErrorParser.map(parsed)
            val reason = when (mapped) { is GeminiErrorMapping.Failure -> mapped.reason; is GeminiErrorMapping.Warning -> FailureReason.UNKNOWN }
            KeyCheckResult.Failed(reason, step, result.code, parsed.apiStatus, parsed.errorReason)
        }
        is HttpResult.Success -> KeyCheckResult.Failed(FailureReason.UNKNOWN, step)
    }

    private fun request(method: String, path: String, key: String, body: String?): HttpResult {
        var connection: HttpURLConnection? = null
        return try {
            connection = (URL("https://generativelanguage.googleapis.com/v1beta/$path").openConnection() as HttpURLConnection).apply {
                requestMethod = method; connectTimeout = 15_000; readTimeout = 30_000; doInput = true
                if (body != null) { doOutput = true; setRequestProperty("Content-Type", "application/json") }
                setRequestProperty("x-goog-api-key", key)
            }
            if (body != null) connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            val code = connection.responseCode
            val stream = if (code in 200..299) connection.inputStream else connection.errorStream
            val response = stream?.use(::readCapped).orEmpty()
            if (code in 200..299) HttpResult.Success(response) else HttpResult.HttpError(code, response)
        } catch (e: SocketTimeoutException) { HttpResult.NetworkError(FailureReason.TIMEOUT) }
        catch (_: IOException) { HttpResult.NetworkError(FailureReason.NO_NETWORK) }
        finally { connection?.disconnect() }
    }

    private data class Model(val name: String, val methods: Set<String>)
    private fun readCapped(input: java.io.InputStream): String {
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8 * 1024)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            if (total > GeminiErrorParser.MAX_BODY_BYTES) break
            output.write(buffer, 0, count)
        }
        return output.toString(Charsets.UTF_8.name())
    }

    private fun parseModels(body: String): List<Model> = runCatching {
        val array = JSONObject(body).optJSONArray("models") ?: JSONArray()
        (0 until array.length()).mapNotNull { index -> array.optJSONObject(index)?.let { model ->
            val methods = model.optJSONArray("supportedGenerationMethods") ?: JSONArray()
            Model(model.optString("name"), (0 until methods.length()).map { i -> methods.optString(i) }.toSet())
        } }
    }.getOrDefault(emptyList())

    private sealed class HttpResult { data class Success(val body: String) : HttpResult(); data class HttpError(val code: Int, val body: String) : HttpResult(); data class NetworkError(val reason: FailureReason) : HttpResult() }
}
