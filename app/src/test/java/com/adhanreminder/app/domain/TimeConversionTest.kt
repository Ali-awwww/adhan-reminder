package com.adhanreminder.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeConversionTest {
    @Test fun convertsAmPmAndArabicDigits() {
        assertEquals("00:15", ExtractionParser.parseTime("12:15", "AM").getOrThrow())
        assertEquals("12:15", ExtractionParser.parseTime("12:15", "PM").getOrThrow())
        assertEquals("16:25", ExtractionParser.parseTime("04:25", "PM").getOrThrow())
        assertEquals("03:26", ExtractionParser.parseTime("٠٣:٢٦", "AM").getOrThrow())
    }
}
