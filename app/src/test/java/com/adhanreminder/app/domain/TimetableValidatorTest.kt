package com.adhanreminder.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class TimetableValidatorTest {
    @Test fun validMonthLengthsAreReady() {
        listOf(28, 29, 30, 31).forEach { count ->
            val result = TimetableValidator.validate(ParsedTimetable("محرم", 1447, 20, days(count), emptyList()))
            assertTrue("count=$count", result.summary.isReady)
            assertEquals(0, result.summary.flaggedCells)
        }
    }

    @Test fun invalidMonthLengthsProduceAdvisoryMonthFlagsOnly() {
        listOf(7, 27, 32).forEach { count ->
            val result = TimetableValidator.validate(ParsedTimetable("محرم", 1447, 20, days(count), emptyList()))
            assertFalse("count=$count", result.summary.isReady)
            assertTrue(result.days.all { day -> day.flags.any { it.field == "month" && TimetableValidator.isAdvisoryFlag(it) } })
            assertTrue(result.days.none { day -> day.flags.any { TimetableValidator.isMandatoryFlag(it) } })
        }
    }

    @Test fun badOrderAndMissingValuesAreMandatoryFlags() {
        val days = days(28).mapIndexed { index, day -> if (index == 0) day.copy(dhuhr = RawTime("12:35", "PM"), asr = RawTime("11:00", "AM")) else day }
        val result = TimetableValidator.validate(ParsedTimetable("محرم", 1447, 20, days, emptyList()))
        assertFalse(result.summary.isReady)
        val dayFlag = result.days.first().flags.first { it.field == "day" && it.reason.contains("ترتيب") }
        assertTrue(TimetableValidator.isMandatoryFlag(dayFlag))
        assertFalse(TimetableValidator.hasValidPrayerOrder(PrayerTimes24("03:26", "12:35", "11:00", "19:55", "21:36")))
        assertTrue(TimetableValidator.hasValidPrayerOrder(PrayerTimes24("03:26", "12:35", "16:25", "19:55", "21:36")))
    }

    @Test fun monthBoundaryDatesMustBeConsecutive() {
        val input = listOf(dayFor("2025-01-31"), dayFor("2025-02-01")) + (2..28).map { dayFor("2025-02-%02d".format(it)) }
        val result = TimetableValidator.validate(ParsedTimetable("رجب", 1447, 20, input, emptyList()))
        assertTrue(result.days.none { it.flags.any { flag -> flag.field == "date" } })
    }

    @Test fun dateGapIsAdvisoryFlagOnCurrentDay() {
        val input = listOf(dayFor("2025-01-01"), dayFor("2025-01-03")) + (4..29).map { dayFor("2025-01-%02d".format(it)) }
        val result = TimetableValidator.validate(ParsedTimetable("رجب", 1447, 20, input, emptyList()))
        val dateFlag = result.days[1].flags.first { it.field == "date" }
        assertTrue(TimetableValidator.isAdvisoryFlag(dateFlag))
    }

    private fun days(count: Int): List<RawPrayerDay> = (1..count).map { dayFor(LocalDate.of(2025, 6, 1).plusDays((it - 1).toLong()).toString()) }

    private fun dayFor(date: String) = RawPrayerDay(date, null, RawTime("03:26", "AM"), RawTime("12:35", "PM"), RawTime("04:25", "PM"), RawTime("07:55", "PM"), RawTime("09:36", "PM"))
}
