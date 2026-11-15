package com.adhanreminder.app.domain

/** Informational format only; it must never be used to reject a key. */
enum class KeyFormat(val code: String) {
    LEGACY_AIZA("legacy_aiza"),
    NEW_AQ("new_aq"),
    UNKNOWN("unknown");

    companion object {
        fun from(key: String): KeyFormat {
            val trimmed = key.trim()
            return when {
                trimmed.startsWith("AIza") -> LEGACY_AIZA
                trimmed.startsWith("AQ.") -> NEW_AQ
                else -> UNKNOWN
            }
        }
    }
}

enum class LocalKeyCheck(val code: String) {
    EMPTY("empty"),
    INTERNAL_WHITESPACE("internal_whitespace")
}

data class ApiKeyValidation(val value: String, val format: KeyFormat, val error: LocalKeyCheck?)

fun inspectApiKey(key: String): ApiKeyValidation {
    val trimmed = key.trim()
    val error = when {
        trimmed.isEmpty() -> LocalKeyCheck.EMPTY
        trimmed.any { it == ' ' || it == '\t' || it == '\n' || it == '\r' } -> LocalKeyCheck.INTERNAL_WHITESPACE
        else -> null
    }
    return ApiKeyValidation(trimmed, KeyFormat.from(trimmed), error)
}

fun maskKey(key: String): String {
    val value = key.trim()
    return if (value.length < 8) "••••••••" else "••••••••" + value.takeLast(4)
}

enum class OkReason(val code: String) { OK_READY("ok_ready") }
enum class WarningReason(val code: String) { QUOTA_LIMITED("quota_limited") }
enum class FailureReason(val code: String) {
    EMPTY("empty"), INTERNAL_WHITESPACE("internal_whitespace"), KEY_INVALID("key_invalid"),
    KEY_EXPIRED("key_expired"), SERVICE_DISABLED("service_disabled"), KEY_RESTRICTED("key_restricted"),
    KEY_NO_PERMISSION("key_no_permission"), BILLING_REQUIRED("billing_required"),
    FREE_TIER_UNAVAILABLE("free_tier_unavailable"), MODEL_NOT_FOUND("model_not_found"),
    MODEL_NO_IMAGE_SUPPORT("model_no_image_support"), SERVICE_BUSY("service_busy"),
    NO_NETWORK("no_network"), TIMEOUT("timeout"), UNKNOWN("unknown")
}

data class GeminiApiError(
    val httpCode: Int?,
    val apiStatus: String?,
    val errorReason: String?,
    val sanitizedMessage: String?,
    val service: String?,
)

sealed class KeyCheckResult {
    data class Ok(val reason: OkReason, val format: KeyFormat, val model: String, val verifiedAtMillis: Long) : KeyCheckResult()
    data class Warning(val reason: WarningReason, val format: KeyFormat, val model: String, val verifiedAtMillis: Long) : KeyCheckResult()
    data class Failed(
        val reason: FailureReason,
        val failedStep: VerificationStep? = null,
        val httpCode: Int? = null,
        val apiStatus: String? = null,
        val errorReason: String? = null,
    ) : KeyCheckResult()
}

enum class VerificationStep { ACCESS, SERVICE, GENERATE }
