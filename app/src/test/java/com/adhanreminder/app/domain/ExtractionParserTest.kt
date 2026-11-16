package com.adhanreminder.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractionParserTest {
    @Test fun parsesGoodJsonAndNormalizesSlashDates() {
        val json = """{"hijriMonthName":"محرم","hijriYear":1447,"fajrNoteMinutes":20,"days":[{"gregorianDate":"2025/6/26","hijriDay":1,"fajr":{"time":"03:26","period":"AM"},"dhuhr":{"time":"12:35","period":"PM"},"asr":{"time":"16:25","period":null},"maghrib":{"time":"7:55","period":"PM"},"isha":{"time":"٠٩:٣٦ م","period":null}}],"flags":[]}"""
        val parsed = ExtractionParser.parse(json).getOrThrow()
        assertEquals("2025-06-26", parsed.days.first().gregorianDate)
        assertEquals("03:26", ExtractionParser.parseTime(parsed.days.first().fajr.time, parsed.days.first().fajr.period).getOrThrow())
        assertEquals("16:25", ExtractionParser.parseTime(parsed.days.first().asr.time, parsed.days.first().asr.period).getOrThrow())
        assertEquals("19:55", ExtractionParser.parseTime(parsed.days.first().maghrib.time, parsed.days.first().maghrib.period).getOrThrow())
        assertEquals("21:36", ExtractionParser.parseTime(parsed.days.first().isha.time, parsed.days.first().isha.period).getOrThrow())
    }

    @Test fun handlesNotTimetableAndMalformedJson() {
        assertTrue(ExtractionParser.parse("{\"error\":\"not_a_prayer_timetable\"}").exceptionOrNull() is ExtractionFailure.NotAPrayerTimetable)
        assertTrue(ExtractionParser.parse("broken").exceptionOrNull() is ExtractionFailure.MalformedJson)
    }

    @Test fun normalizesArabicDigitsSingleDigitHours24hAndRejectsInvalidTimesAndCounts() {
        assertEquals("03:26", ExtractionParser.parseTime("٠٣:٢٦", "AM").getOrThrow())
        assertEquals("03:26", ExtractionParser.parseTime("3:26", "AM").getOrThrow())
        assertEquals("15:40", ExtractionParser.parseTime("15:40", null).getOrThrow())
        assertEquals("15:40", ExtractionParser.parseTime("15:40", "PM").getOrThrow())
        assertTrue(ExtractionParser.parseTime("25:00", null).isFailure)
        assertTrue(ExtractionParser.parseTime("12:65", "PM").isFailure)
        val tooManyDays = "{\"days\":[${(1..32).joinToString(",") { "{\"gregorianDate\":\"2025-01-01\"}" }}]}"
        assertTrue(ExtractionParser.parse(tooManyDays).exceptionOrNull() is ExtractionFailure.InvalidDayCount)
    }

    @Test fun normalizesVariousDateFormats() {
        assertEquals("2025-06-26", ExtractionParser.normalizeDate("2025/6/26"))
        assertEquals("2025-07-01", ExtractionParser.normalizeDate("٢٠٢٥/٧/١"))
        assertEquals("2025-06-26", ExtractionParser.normalizeDate("26/06/2025"))
        assertEquals("2025-06-26", ExtractionParser.normalizeDate("2025-06-26"))
    }

    @Test fun parsesMarkdownFencedAndWrappedJson() {
        val wrapped = """
            Here is the extracted timetable:
            ```json
            {"hijriMonthName":"محرم","hijriYear":1447,"days":[{"gregorianDate":"2025-06-26","hijriDay":1,"fajr":{"time":"03:26","period":"AM"},"dhuhr":{"time":"12:35","period":"PM"},"asr":{"time":"16:25","period":null},"maghrib":{"time":"19:55","period":null},"isha":{"time":"21:36","period":null}}],"flags":[]}
            ```
        """.trimIndent()
        val parsed = ExtractionParser.parse(wrapped).getOrThrow()
        assertEquals(1, parsed.days.size)
        assertEquals("2025-06-26", parsed.days.first().gregorianDate)
    }
}
