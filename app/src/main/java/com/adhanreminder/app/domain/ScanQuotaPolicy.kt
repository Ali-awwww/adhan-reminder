package com.adhanreminder.app.domain

import java.time.LocalDate
import java.util.Locale
import kotlin.math.ceil

data class ScanQuotaRecord(
    val dayKey: String = "",
    val dailyCount: Int = 0,
    val monthKey: String = "",
    val monthlyCount: Int = 0,
    val lastAttemptMillis: Long = 0L,
)

data class ScanQuotaStatus(
    val dailyRemaining: Int,
    val monthlyRemaining: Int,
    val cooldownRemainingSeconds: Int,
    val canScan: Boolean,
    val blockReasonAr: String?,
)

object ScanQuotaPolicy {
    const val MAX_DAILY_SCANS = 5
    const val MAX_MONTHLY_SCANS = 15
    const val COOLDOWN_SECONDS = 10

    fun dayKey(date: LocalDate): String = date.toString()

    fun monthKey(date: LocalDate): String =
        "%04d-%02d".format(Locale.ROOT, date.year, date.monthValue)

    fun evaluate(
        record: ScanQuotaRecord,
        nowMillis: Long,
        today: LocalDate = LocalDate.now(),
    ): ScanQuotaStatus {
        val currentDay = dayKey(today)
        val currentMonth = monthKey(today)
        val effectiveDaily = if (record.dayKey == currentDay) record.dailyCount.coerceAtLeast(0) else 0
        val effectiveMonthly = if (record.monthKey == currentMonth) record.monthlyCount.coerceAtLeast(0) else 0

        val dailyRemaining = (MAX_DAILY_SCANS - effectiveDaily).coerceAtLeast(0)
        val monthlyRemaining = (MAX_MONTHLY_SCANS - effectiveMonthly).coerceAtLeast(0)

        val remainingMs = (record.lastAttemptMillis + COOLDOWN_SECONDS * 1000L) - nowMillis
        val cooldownRemainingSeconds = if (record.lastAttemptMillis > 0L && remainingMs > 0L) {
            ceil(remainingMs / 1000.0).toInt().coerceIn(1, COOLDOWN_SECONDS)
        } else {
            0
        }

        val blockReasonAr = when {
            monthlyRemaining == 0 -> "بلغت الحد الشهري لقراءة الجداول ($MAX_MONTHLY_SCANS محاولة). يتجدد الرصيد بداية الشهر القادم."
            dailyRemaining == 0 -> "بلغت الحد اليومي لقراءة الجداول ($MAX_DAILY_SCANS محاولات). يمكنك المحاولة غداً."
            cooldownRemainingSeconds > 0 -> "انتظر $cooldownRemainingSeconds ثانية قبل المحاولة التالية."
            else -> null
        }

        return ScanQuotaStatus(
            dailyRemaining = dailyRemaining,
            monthlyRemaining = monthlyRemaining,
            cooldownRemainingSeconds = cooldownRemainingSeconds,
            canScan = blockReasonAr == null,
            blockReasonAr = blockReasonAr,
        )
    }

    fun recordAttempt(
        record: ScanQuotaRecord,
        nowMillis: Long,
        today: LocalDate = LocalDate.now(),
    ): ScanQuotaRecord {
        val currentDay = dayKey(today)
        val currentMonth = monthKey(today)
        val effectiveDaily = if (record.dayKey == currentDay) record.dailyCount.coerceAtLeast(0) else 0
        val effectiveMonthly = if (record.monthKey == currentMonth) record.monthlyCount.coerceAtLeast(0) else 0
        return ScanQuotaRecord(
            dayKey = currentDay,
            dailyCount = effectiveDaily + 1,
            monthKey = currentMonth,
            monthlyCount = effectiveMonthly + 1,
            lastAttemptMillis = nowMillis,
        )
    }
}
