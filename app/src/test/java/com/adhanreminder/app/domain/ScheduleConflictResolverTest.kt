package com.adhanreminder.app.domain

import com.adhanreminder.app.data.local.PrayerKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleConflictResolverTest {
    private fun day(
        date: String,
        fajr: String = "04:30",
        dhuhr: String = "12:15",
        asr: String = "15:40",
        maghrib: String = "18:50",
        isha: String = "20:15",
        hijriDay: Int? = 1,
    ) = ScheduleDaySnapshot(
        gregorianDate = date,
        hijriDay = hijriDay,
        fajr = fajr,
        dhuhr = dhuhr,
        asr = asr,
        maghrib = maghrib,
        isha = isha,
    )

    @Test fun noConflictWhenDatesDoNotOverlap() {
        val oldDays = listOf(day("2026-11-01"), day("2026-11-02"))
        val newDays = listOf(day("2026-11-10"), day("2026-11-11"))

        val report = ScheduleConflictResolver.detectConflicts(1L, "الجدول الأول", oldDays, newDays)

        assertFalse(report.hasConflicts)
        assertEquals(0, report.conflictingDateCount)
        assertEquals(2, report.nonOverlappingOldDayCount)
    }

    @Test fun noConflictWhenOverlappingDatesHaveIdenticalTimes() {
        val oldDays = listOf(day("2026-11-10", fajr = "04:45"), day("2026-11-11"))
        val newDays = listOf(day("2026-11-10", fajr = "04:45"), day("2026-11-12"))

        val report = ScheduleConflictResolver.detectConflicts(1L, "الجدول الأول", oldDays, newDays)

        assertFalse(report.hasConflicts)
        assertEquals(0, report.conflicts.size)
        assertEquals(1, report.nonOverlappingOldDayCount)
    }

    @Test fun detectsChangedPrayerTimesOnOverlappingDates() {
        val oldDays = listOf(
            day("2026-11-09"),
            day("2026-11-10", fajr = "04:45", maghrib = "18:40"),
            day("2026-11-11", isha = "20:10"),
        )
        val newDays = listOf(
            day("2026-11-10", fajr = "04:50", maghrib = "18:40"),
            day("2026-11-11", isha = "20:20"),
            day("2026-11-12"),
        )

        val report = ScheduleConflictResolver.detectConflicts(7L, "محرم 1448", oldDays, newDays)

        assertTrue(report.hasConflicts)
        assertEquals(7L, report.existingScheduleId)
        assertEquals(2, report.conflictingDateCount)
        assertEquals(1, report.nonOverlappingOldDayCount)
        assertEquals(2, report.conflicts.size)
        assertEquals(PrayerKey.FAJR, report.conflicts[0].prayerKey)
        assertEquals("04:45", report.conflicts[0].existingTime)
        assertEquals("04:50", report.conflicts[0].newTime)
        assertEquals(PrayerKey.ISHA, report.conflicts[1].prayerKey)
    }

    @Test fun mergeKeepsNonOverlappingOldDaysAndPrefersNewerOnOverlap() {
        val oldDays = listOf(
            day("2026-11-08", fajr = "04:40"),
            day("2026-11-10", fajr = "04:45"),
        )
        val newDays = listOf(
            day("2026-11-10", fajr = "04:52"),
            day("2026-11-09", fajr = "04:49"),
        )

        val merged = ScheduleConflictResolver.mergeDays(oldDays, newDays)

        assertEquals(listOf("2026-11-08", "2026-11-09", "2026-11-10"), merged.map { it.gregorianDate })
        assertEquals("04:40", merged[0].fajr)
        assertEquals("04:49", merged[1].fajr)
        assertEquals("04:52", merged[2].fajr)
    }
}
