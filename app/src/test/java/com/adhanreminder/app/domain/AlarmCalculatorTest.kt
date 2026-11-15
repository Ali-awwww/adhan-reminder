package com.adhanreminder.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class AlarmCalculatorTest {
    private val day = ValidatedDay(LocalDate.of(2025, 6, 26), "2025-06-26", 1, PrayerTimes24("03:26", "12:35", "16:25", "19:55", "21:36"), mutableListOf())

    @Test fun goldenSampleChecksAllFiveTriggers() {
        val result = AlarmCalculator.calculate(day, 10, 20, ZoneId.of("UTC"), LocalDateTime.of(2025, 6, 25, 0, 0))
        assertEquals(5, result.plans.size)
        assertEquals("02:56", result.plans[0].triggerDateTime.toLocalTime().toString())
        assertEquals("12:25", result.plans[1].triggerDateTime.toLocalTime().toString())
        assertEquals("16:15", result.plans[2].triggerDateTime.toLocalTime().toString())
        assertEquals("19:45", result.plans[3].triggerDateTime.toLocalTime().toString())
        assertEquals("21:26", result.plans[4].triggerDateTime.toLocalTime().toString())
        result.plans.forEach { assertEquals(LocalDate.of(2025, 6, 26), it.triggerDateTime.toLocalDate()) }
        assertTrue(result.plans.none { it.prayerKey.name == "SHOROUK" })
    }

    @Test fun crossesMidnightAndUsesPreviousDate() {
        val midnight = day.copy(times = day.times.copy(fajr = "00:20"))
        val result = AlarmCalculator.calculate(midnight, 10, 20, ZoneId.of("UTC"), LocalDateTime.of(2025, 6, 25, 0, 0))
        assertEquals(LocalDate.of(2025, 6, 25), result.plans.first().triggerDateTime.toLocalDate())
        assertEquals("23:50", result.plans.first().triggerDateTime.toLocalTime().toString())
    }

    @Test fun missingTimeProducesFewerPlans() {
        val result = AlarmCalculator.calculate(day.copy(times = day.times.copy(isha = null)), 10, 20, ZoneId.of("UTC"), LocalDateTime.of(2025, 6, 25, 0, 0))
        assertEquals(4, result.plans.size)
        assertTrue(result.missingPrayerKeys.contains(com.adhanreminder.app.data.local.PrayerKey.ISHA))
    }

    @Test fun dstGapUsesAnExplicitTransitionPolicy() {
        val dstDay = day.copy(date = LocalDate.of(2025, 3, 9), gregorianDate = "2025-03-09", times = day.times.copy(fajr = "03:00", dhuhr = null, asr = null, maghrib = null, isha = null))
        val result = AlarmCalculator.calculate(dstDay, 10, 20, ZoneId.of("America/New_York"), LocalDateTime.of(2025, 3, 8, 0, 0))
        assertEquals("03:00", result.plans.first().triggerDateTime.toLocalTime().toString())
    }
}
