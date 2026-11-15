package com.adhanreminder.app.domain

import org.json.JSONObject
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.Locale

sealed class GeminiErrorMapping {
    data class Failure(val reason: FailureReason) : GeminiErrorMapping()
    data class Warning(val reason: WarningReason) : GeminiErrorMapping()
}

/** Pure Kotlin parser shared by key verification and timetable requests. */
object GeminiErrorParser {
    const val MAX_BODY_BYTES = 64 * 1024
    const val MAX_MESSAGE_CHARS = 120

    fun parse(httpCode: Int?, body: String?): GeminiApiError {
        if (body == null || body.toByteArray(Charsets.UTF_8).size > MAX_BODY_BYTES) {
            return GeminiApiError(httpCode, null, null, null, null)
        }
        return try {
            val error = JSONObject(body).optJSONObject("error")
                ?: return GeminiApiError(httpCode, null, null, null, null)
            val details = error.optJSONArray("details")
            var reason: String? = null
            var service: String? = null
            if (details != null) {
                for (index in 0 until details.length()) {
                    val detail = details.optJSONObject(index) ?: continue
                    val type = detail.optString("@type")
                    if (type.endsWith("ErrorInfo")) {
                        reason = detail.optString("reason").takeUnless { it.isBlank() }
                        val metadata = detail.optJSONObject("metadata")
                        service = metadata?.optString("service")?.takeUnless { it.isNullOrBlank() }
                        break
                    }
                }
            }
            GeminiApiError(
                httpCode = httpCode,
                apiStatus = error.optString("status").takeUnless { it.isBlank() },
                errorReason = reason,
                sanitizedMessage = sanitize(error.optString("message").takeUnless { it.isBlank() }),
                service = service,
            )
        } catch (_: Exception) {
            GeminiApiError(httpCode, null, null, null, null)
        }
    }

    fun sanitize(message: String?): String? {
        if (message == null) return null
        val redacted = message
            .replace(Regex("(?i)Bearer\\s+[A-Za-z0-9._~+/=-]+"), "Bearer [redacted]")
            .replace(Regex("(?i)(api[_ -]?key|token|key)(\\s*[:=]\\s*)[A-Za-z0-9._~+/=-]{8,}"), "$1$2[redacted]")
            .replace(Regex("(?:AIza[0-9A-Za-z_-]{20,}|AQ\\.[0-9A-Za-z._~-]{8,})"), "[redacted]")
        return redacted.take(MAX_MESSAGE_CHARS)
    }

    fun map(error: GeminiApiError): GeminiErrorMapping {
        knownReason(error.errorReason)?.let { return it }
        val status = error.apiStatus.orEmpty()
        val message = error.sanitizedMessage.orEmpty().lowercase(Locale.ROOT)
        if (status == "FAILED_PRECONDITION" && message.contains("free tier")) {
            return GeminiErrorMapping.Failure(FailureReason.FREE_TIER_UNAVAILABLE)
        }
        return when {
            error.httpCode == 402 -> GeminiErrorMapping.Failure(FailureReason.BILLING_REQUIRED)
            error.httpCode == 429 -> GeminiErrorMapping.Warning(WarningReason.QUOTA_LIMITED)
            error.httpCode == 403 && status == "PERMISSION_DENIED" -> when {
                "blocked" in message || "restrict" in message -> GeminiErrorMapping.Failure(FailureReason.KEY_RESTRICTED)
                "not been used" in message || "disabled" in message -> GeminiErrorMapping.Failure(FailureReason.SERVICE_DISABLED)
                "billing" in message -> GeminiErrorMapping.Failure(FailureReason.BILLING_REQUIRED)
                else -> GeminiErrorMapping.Failure(FailureReason.KEY_NO_PERMISSION)
            }
            error.httpCode == 400 && apiKeyMessage(message) -> GeminiErrorMapping.Failure(FailureReason.KEY_INVALID)
            error.httpCode == 404 -> GeminiErrorMapping.Failure(FailureReason.MODEL_NOT_FOUND)
            error.httpCode == 500 || error.httpCode == 503 || error.httpCode == 504 -> GeminiErrorMapping.Failure(FailureReason.SERVICE_BUSY)
            else -> GeminiErrorMapping.Failure(FailureReason.UNKNOWN)
        }
    }

    fun mapLocal(exception: IOException): FailureReason =
        if (exception is SocketTimeoutException) FailureReason.TIMEOUT else FailureReason.NO_NETWORK

    fun classify(httpCode: Int?, body: String?): GeminiErrorMapping = map(parse(httpCode, body))

    private fun apiKeyMessage(message: String): Boolean =
        message.contains("api key") || message.contains("apikey") || message.contains("api_key") || message.contains("invalid key")

    private fun knownReason(reason: String?): GeminiErrorMapping? = when (reason) {
        "API_KEY_INVALID" -> GeminiErrorMapping.Failure(FailureReason.KEY_INVALID)
        "API_KEY_EXPIRED" -> GeminiErrorMapping.Failure(FailureReason.KEY_EXPIRED)
        "SERVICE_DISABLED" -> GeminiErrorMapping.Failure(FailureReason.SERVICE_DISABLED)
        "API_KEY_SERVICE_BLOCKED", "API_KEY_HTTP_REFERRER_BLOCKED", "API_KEY_ANDROID_APP_BLOCKED", "API_KEY_IP_ADDRESS_BLOCKED" -> GeminiErrorMapping.Failure(FailureReason.KEY_RESTRICTED)
        "BILLING_DISABLED" -> GeminiErrorMapping.Failure(FailureReason.BILLING_REQUIRED)
        "RATE_LIMIT_EXCEEDED" -> GeminiErrorMapping.Warning(WarningReason.QUOTA_LIMITED)
        else -> null
    }
}
