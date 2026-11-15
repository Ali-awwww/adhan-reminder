package com.adhanreminder.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.SocketTimeoutException

class GeminiErrorParserTest {
    private fun body(code: Int = 400, status: String = "INVALID_ARGUMENT", message: String = "bad", reason: String? = null): String {
        val detail = reason?.let { ",\"details\":[{\"@type\":\"type.googleapis.com/google.rpc.ErrorInfo\",\"reason\":\"$it\",\"metadata\":{\"service\":\"generativelanguage.googleapis.com\"}}]" } ?: ""
        return "{\"error\":{\"code\":$code,\"message\":\"$message\",\"status\":\"$status\"$detail}}"
    }

    @Test fun parsesErrorInfoAndSanitizesAndCapsMessage() {
        val key = "AIza123456789012345678901234567890"
        val parsed = GeminiErrorParser.parse(400, body(message = "key=$key".padEnd(140, 'x'), reason = "API_KEY_INVALID"))
        assertEquals(400, parsed.httpCode)
        assertEquals("INVALID_ARGUMENT", parsed.apiStatus)
        assertEquals("API_KEY_INVALID", parsed.errorReason)
        assertEquals("generativelanguage.googleapis.com", parsed.service)
        assertTrue((parsed.sanitizedMessage ?: "").length <= 120)
        assertTrue(!(parsed.sanitizedMessage ?: "").contains(key))
    }

    @Test fun malformedAndOversizedBodiesNeverThrowOrParse() {
        val malformed = GeminiErrorParser.parse(418, "not-json")
        assertEquals(418, malformed.httpCode)
        assertNull(malformed.apiStatus)
        assertNull(malformed.errorReason)
        assertNull(malformed.sanitizedMessage)
        val oversized = GeminiErrorParser.parse(500, "x".repeat(64 * 1024 + 1))
        assertNull(oversized.apiStatus)
    }

    @Test fun mapsEveryKnownReasonRow() {
        val expected = mapOf(
            "API_KEY_INVALID" to FailureReason.KEY_INVALID,
            "API_KEY_EXPIRED" to FailureReason.KEY_EXPIRED,
            "SERVICE_DISABLED" to FailureReason.SERVICE_DISABLED,
            "API_KEY_SERVICE_BLOCKED" to FailureReason.KEY_RESTRICTED,
            "API_KEY_HTTP_REFERRER_BLOCKED" to FailureReason.KEY_RESTRICTED,
            "API_KEY_ANDROID_APP_BLOCKED" to FailureReason.KEY_RESTRICTED,
            "API_KEY_IP_ADDRESS_BLOCKED" to FailureReason.KEY_RESTRICTED,
            "BILLING_DISABLED" to FailureReason.BILLING_REQUIRED,
        )
        expected.forEach { (reason, expectedReason) ->
            val mapping = GeminiErrorParser.map(GeminiErrorParser.parse(400, body(reason = reason)))
            assertEquals(expectedReason, (mapping as GeminiErrorMapping.Failure).reason)
        }
        assertTrue(GeminiErrorParser.map(GeminiErrorParser.parse(429, body(reason = "RATE_LIMIT_EXCEEDED"))) is GeminiErrorMapping.Warning)
    }

    @Test fun appliesPrecedenceAndHttpFallbacks() {
        assertEquals(FailureReason.FREE_TIER_UNAVAILABLE, failure(400, "free tier unavailable", "FAILED_PRECONDITION"))
        assertEquals(FailureReason.BILLING_REQUIRED, failure(402, "anything"))
        assertTrue(GeminiErrorParser.map(GeminiErrorParser.parse(429, body(429, message = "quota"))) is GeminiErrorMapping.Warning)
        assertEquals(FailureReason.KEY_RESTRICTED, failure(403, "request blocked", "PERMISSION_DENIED"))
        assertEquals(FailureReason.SERVICE_DISABLED, failure(403, "service has not been used", "PERMISSION_DENIED"))
        assertEquals(FailureReason.BILLING_REQUIRED, failure(403, "billing required", "PERMISSION_DENIED"))
        assertEquals(FailureReason.KEY_NO_PERMISSION, failure(403, "permission denied", "PERMISSION_DENIED"))
        assertEquals(FailureReason.KEY_INVALID, failure(400, "API key is invalid"))
        assertEquals(FailureReason.MODEL_NOT_FOUND, failure(404, "missing"))
        assertEquals(FailureReason.SERVICE_BUSY, failure(503, "busy"))
        assertEquals(FailureReason.UNKNOWN, failure(418, "unknown"))
    }

    @Test fun mapsNetworkExceptions() {
        assertEquals(FailureReason.TIMEOUT, GeminiErrorParser.mapLocal(SocketTimeoutException()))
        assertEquals(FailureReason.NO_NETWORK, GeminiErrorParser.mapLocal(java.io.IOException()))
    }

    private fun failure(code: Int, message: String, status: String = "INVALID_ARGUMENT"): FailureReason {
        return (GeminiErrorParser.map(GeminiErrorParser.parse(code, body(code, status, message))) as GeminiErrorMapping.Failure).reason
    }
}
