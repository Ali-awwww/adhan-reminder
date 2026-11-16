package com.adhanreminder.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ScanQuotaPolicyTest {
    @Test fun enforcesCooldownDailyAndMonthlyLimits() {
        val today = LocalDate.of(2026, 11, 16)
        var record = ScanQuotaRecord()
        val t0 = 1_000_000L

        val initial = ScanQuotaPolicy.evaluate(record, t0, today)
        assertTrue(initial.canScan)
        assertEquals(5, initial.dailyRemaining)
        assertEquals(15, initial.monthlyRemaining)
        assertEquals(0, initial.cooldownRemainingSeconds)

        record = ScanQuotaPolicy.recordAttempt(record, t0, today)
        val duringCooldown = ScanQuotaPolicy.evaluate(record, t0 + 3_500L, today)
        assertFalse(duringCooldown.canScan)
        assertEquals(4, duringCooldown.dailyRemaining)
        assertEquals(14, duringCooldown.monthlyRemaining)
        assertEquals(7, duringCooldown.cooldownRemainingSeconds)
        assertNotNull(duringCooldown.blockReasonAr)

        val afterCooldown = ScanQuotaPolicy.evaluate(record, t0 + 10_000L, today)
        assertTrue(afterCooldown.canScan)
        assertEquals(0, afterCooldown.cooldownRemainingSeconds)
        assertNull(afterCooldown.blockReasonAr)

        var time = t0 + 20_000L
        repeat(4) {
            record = ScanQuotaPolicy.recordAttempt(record, time, today)
            time += 15_000L
        }
        val atDailyLimit = ScanQuotaPolicy.evaluate(record, time, today)
        assertFalse(atDailyLimit.canScan)
        assertEquals(0, atDailyLimit.dailyRemaining)
        assertEquals(10, atDailyLimit.monthlyRemaining)
    }

    @Test fun resetsDailyAndMonthlyCountersOnRollover() {
        val day1 = LocalDate.of(2026, 11, 16)
        val day2 = LocalDate.of(2026, 11, 17)
        val nextMonth = LocalDate.of(2026, 12, 1)

        var record = ScanQuotaRecord(
            dayKey = ScanQuotaPolicy.dayKey(day1),
            dailyCount = 5,
            monthKey = ScanQuotaPolicy.monthKey(day1),
            monthlyCount = 15,
            lastAttemptMillis = 1_000_000L,
        )

        val sameMonthNextDay = ScanQuotaPolicy.evaluate(record, 2_000_000L, day2)
        assertEquals(5, sameMonthNextDay.dailyRemaining)
        assertEquals(0, sameMonthNextDay.monthlyRemaining)
        assertFalse(sameMonthNextDay.canScan)

        val newMonth = ScanQuotaPolicy.evaluate(record, 3_000_000L, nextMonth)
        assertEquals(5, newMonth.dailyRemaining)
        assertEquals(15, newMonth.monthlyRemaining)
        assertTrue(newMonth.canScan)
    }
}
