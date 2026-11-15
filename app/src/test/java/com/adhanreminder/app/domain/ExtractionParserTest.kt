package com.adhanreminder.app.domain

import org.junit.Assert.assertTrue
import org.junit.Test

class ExtractionParserTest {
    @Test fun parsesGoodJson() {
        val json = """{"hijriMonthName":"محرم","hijriYear":1447,"fajrNoteMinutes":20,"days":[{"gregorianDate":"2025-06-26","hijriDay":1,"fajr":{"time":"03:26","period":"AM"},"dhuhr":{"time":"12:35","period":"PM"},"asr":{"time":"04:25","period":"PM"},"maghrib":{"time":"07:55","period":"PM"},"isha":{"time":"09:36","period":"PM"}}],"flags":[]}"""
        assertTrue(ExtractionParser.parse(json).isSuccess)
    }
    @Test fun handlesNotTimetableAndMalformedJson() {
        assertTrue(ExtractionParser.parse("{\"error\":\"not_a_prayer_timetable\"}").exceptionOrNull() is ExtractionFailure.NotAPrayerTimetable)
        assertTrue(ExtractionParser.parse("broken").exceptionOrNull() is ExtractionFailure.MalformedJson)
    }

    @Test fun normalizesArabicDigitsAndRejectsLooseTimesAndDayCounts() {
        assertTrue(ExtractionParser.parseTime("٠٣:٢٦", "AM").getOrNull() == "03:26")
        assertTrue(ExtractionParser.parseTime("3:26", "AM").isFailure)
        val tooManyDays = "{\"days\":[${(1..32).joinToString(",") { "{\"gregorianDate\":\"2025-01-01\"}" }}]}"
        assertTrue(ExtractionParser.parse(tooManyDays).exceptionOrNull() is ExtractionFailure.InvalidDayCount)
    }
}
