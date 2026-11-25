package com.adhanreminder.app.domain

import com.adhanreminder.app.data.local.PrayerKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class AlarmCalculatorTest {
    private val day = ValidatedDay(
        LocalDate.of(2025, 6, 26),
        "2025-06-26",
        1,
        PrayerTimes24("03:26", "12:35", "16:25", "19:55", "21:36"),
        mutableListOf(),
    )

    @Test fun safirahDaySchedulesExactTenMinutesBeforeFajrAndDhuhrWithNoSunriseAlarm() {
        val safirahDay = ValidatedDay(
            date = LocalDate.of(2026, 11, 25),
            gregorianDate = "2026-11-25",
            hijriDay = 15,
            times = PrayerTimes24(
                fajr = "05:47",
                dhuhr = "12:19",
                asr = "14:59",
                maghrib = "17:20",
                isha = "18:43",
            ),
            flags = mutableListOf(),
        )
        val result = AlarmCalculator.calculate(
            day = safirahDay,
            reminderOffsetMinutes = 10,
            zoneId = ZoneId.of("UTC"),
            now = LocalDateTime.of(2026, 11, 25, 0, 0),
        )
        assertEquals(5, result.plans.size)
        val byPrayer = result.plans.associateBy { it.prayerKey }
        assertEquals("05:37", byPrayer[PrayerKey.FAJR]?.triggerDateTime?.toLocalTime()?.toString())
        assertEquals("12:09", byPrayer[PrayerKey.DHUHR]?.triggerDateTime?.toLocalTime()?.toString())
        assertEquals("14:49", byPrayer[PrayerKey.ASR]?.triggerDateTime?.toLocalTime()?.toString())
        assertEquals("17:10", byPrayer[PrayerKey.MAGHRIB]?.triggerDateTime?.toLocalTime()?.toString())
        assertEquals("18:33", byPrayer[PrayerKey.ISHA]?.triggerDateTime?.toLocalTime()?.toString())
        assertTrue(PrayerKey.entries.none { it.name == "SHOROUK" || it.name == "SUNRISE" })
        assertEquals(
            setOf(PrayerKey.FAJR, PrayerKey.DHUHR, PrayerKey.ASR, PrayerKey.MAGHRIB, PrayerKey.ISHA),
            byPrayer.keys,
        )
    }

    @Test fun goldenSampleChecksAllFiveTriggers() {
        val result = AlarmCalculator.calculate(day, 10, ZoneId.of("UTC"), LocalDateTime.of(2025, 6, 25, 0, 0))
        assertEquals(5, result.plans.size)
        assertEquals("03:16", result.plans[0].triggerDateTime.toLocalTime().toString())
        assertEquals("12:25", result.plans[1].triggerDateTime.toLocalTime().toString())
        assertEquals("16:15", result.plans[2].triggerDateTime.toLocalTime().toString())
        assertEquals("19:45", result.plans[3].triggerDateTime.toLocalTime().toString())
        assertEquals("21:26", result.plans[4].triggerDateTime.toLocalTime().toString())
        result.plans.forEach { assertEquals(LocalDate.of(2025, 6, 26), it.triggerDateTime.toLocalDate()) }
        assertTrue(result.plans.none { it.prayerKey.name == "SHOROUK" })
    }

    @Test fun crossesMidnightAndUsesPreviousDate() {
        val midnight = day.copy(times = day.times.copy(fajr = "00:05"))
        val result = AlarmCalculator.calculate(midnight, 10, ZoneId.of("UTC"), LocalDateTime.of(2025, 6, 25, 0, 0))
        assertEquals(LocalDate.of(2025, 6, 25), result.plans.first().triggerDateTime.toLocalDate())
        assertEquals("23:55", result.plans.first().triggerDateTime.toLocalTime().toString())
    }

    @Test fun missingTimeProducesFewerPlans() {
        val result = AlarmCalculator.calculate(day.copy(times = day.times.copy(isha = null)), 10, ZoneId.of("UTC"), LocalDateTime.of(2025, 6, 25, 0, 0))
        assertEquals(4, result.plans.size)
        assertTrue(result.missingPrayerKeys.contains(PrayerKey.ISHA))
    }

    @Test fun dstGapUsesAnExplicitTransitionPolicy() {
        val dstDay = day.copy(
            date = LocalDate.of(2025, 3, 9),
            gregorianDate = "2025-03-09",
            times = day.times.copy(fajr = "02:40", dhuhr = null, asr = null, maghrib = null, isha = null),
        )
        val result = AlarmCalculator.calculate(dstDay, 10, ZoneId.of("America/New_York"), LocalDateTime.of(2025, 3, 8, 0, 0))
        assertEquals("03:00", result.plans.first().triggerDateTime.toLocalTime().toString())
    }
}
