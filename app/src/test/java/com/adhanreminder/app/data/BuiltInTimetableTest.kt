package com.adhanreminder.app.data

import com.adhanreminder.app.domain.PrayerTimes24
import com.adhanreminder.app.domain.TimetableValidator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.temporal.ChronoUnit

class BuiltInTimetableTest {
    @Test fun safirahTimetableHas29ConsecutiveDaysWithExactBoundaryAndMidMonthTimes() {
        val days = BuiltInTimetable.days
        assertEquals(29, days.size)
        assertEquals("جدول السفيرة — جمادى الآخرة 1448", BuiltInTimetable.LABEL)

        val first = days.first()
        assertEquals("2026-11-11", first.gregorianDate)
        assertEquals(1, first.hijriDay)
        assertEquals("05:35", first.fajr)
        assertEquals("07:03", first.sunrise)
        assertEquals("12:16", first.dhuhr)
        assertEquals("15:05", first.asr)
        assertEquals("17:27", first.maghrib)
        assertEquals("18:49", first.isha)

        val mid = days.first { it.gregorianDate == "2026-11-25" }
        assertEquals(15, mid.hijriDay)
        assertEquals("05:47", mid.fajr)
        assertEquals("07:17", mid.sunrise)
        assertEquals("12:19", mid.dhuhr)
        assertEquals("14:59", mid.asr)
        assertEquals("17:20", mid.maghrib)
        assertEquals("18:43", mid.isha)

        val last = days.last()
        assertEquals("2026-12-09", last.gregorianDate)
        assertEquals(29, last.hijriDay)
        assertEquals("05:58", last.fajr)
        assertEquals("07:30", last.sunrise)
        assertEquals("12:24", last.dhuhr)
        assertEquals("14:58", last.asr)
        assertEquals("17:18", last.maghrib)
        assertEquals("18:43", last.isha)

        days.forEachIndexed { index, day ->
            assertEquals(index + 1, day.hijriDay)
            assertTrue(
                "Invalid order on ${day.gregorianDate}",
                TimetableValidator.hasValidPrayerOrder(
                    PrayerTimes24(day.fajr, day.dhuhr, day.asr, day.maghrib, day.isha),
                ),
            )
        }

        days.zipWithNext().forEach { (prev, curr) ->
            val diff = ChronoUnit.DAYS.between(LocalDate.parse(prev.gregorianDate), LocalDate.parse(curr.gregorianDate))
            assertEquals(1L, diff)
        }
    }

    @Test fun toPrayerDaysMapsOnlyFivePrayersWithoutSunrise() {
        val entities = BuiltInTimetable.toPrayerDays(scheduleId = 7L)
        assertEquals(29, entities.size)
        val nov25 = entities.first { it.gregorianDate == "2026-11-25" }
        assertEquals(7L, nov25.scheduleId)
        assertEquals("05:47", nov25.fajr)
        assertEquals("12:19", nov25.dhuhr)
        assertEquals("14:59", nov25.asr)
        assertEquals("17:20", nov25.maghrib)
        assertEquals("18:43", nov25.isha)
    }
}
