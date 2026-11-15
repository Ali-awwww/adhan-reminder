package com.adhanreminder.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ApiKeyModelTest {
    @Test fun detectsFormatsWithoutRejectingUnknown() {
        assertEquals(KeyFormat.LEGACY_AIZA, KeyFormat.from("  AIza-test"))
        assertEquals(KeyFormat.NEW_AQ, KeyFormat.from("AQ.test"))
        assertEquals(KeyFormat.UNKNOWN, KeyFormat.from("custom-key"))
    }

    @Test fun trimsOnlyOuterWhitespaceAndDetectsInternalWhitespace() {
        assertEquals("AIza-test", inspectApiKey("  AIza-test  ").value)
        assertNull(inspectApiKey("  AIza-test  ").error)
        assertEquals(LocalKeyCheck.EMPTY, inspectApiKey(" \t\n").error)
        assertEquals(LocalKeyCheck.INTERNAL_WHITESPACE, inspectApiKey("AIza test").error)
        assertEquals(LocalKeyCheck.INTERNAL_WHITESPACE, inspectApiKey("AIza\ttest").error)
        assertEquals(LocalKeyCheck.INTERNAL_WHITESPACE, inspectApiKey("AIza\ntest").error)
    }

    @Test fun masksOnlyTheAllowedDisplayForm() {
        assertEquals("••••••••", maskKey("short"))
        assertEquals("••••••••5678", maskKey("12345678"))
        assertEquals("••••••••5678", maskKey("  abcdefgh5678  "))
    }
}
