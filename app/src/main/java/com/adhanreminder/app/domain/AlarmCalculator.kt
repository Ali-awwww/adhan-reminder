package com.adhanreminder.app.domain

import com.adhanreminder.app.data.local.PrayerKey
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

object AlarmCalculator {
    fun calculate(
        day: ValidatedDay,
        reminderOffsetMinutes: Int,
        zoneId: ZoneId,
        now: LocalDateTime,
    ): AlarmCalculation {
        require(reminderOffsetMinutes in 1..60)
        val date = day.date ?: runCatching { LocalDate.parse(day.gregorianDate) }.getOrElse { return AlarmCalculation(emptyList(), PrayerKey.entries.toList()) }
        val entries = listOf(
            PrayerKey.FAJR to day.times.fajr,
            PrayerKey.DHUHR to day.times.dhuhr,
            PrayerKey.ASR to day.times.asr,
            PrayerKey.MAGHRIB to day.times.maghrib,
            PrayerKey.ISHA to day.times.isha,
        )
        val plans = mutableListOf<AlarmPlan>()
        val missing = mutableListOf<PrayerKey>()
        entries.forEach { (key, value) ->
            if (value == null) {
                missing += key
            } else {
                val adhanTime = LocalTime.parse(value)
                val requestedTrigger = LocalDateTime.of(date, adhanTime).minusMinutes(reminderOffsetMinutes.toLong())
                val resolved = resolveDst(requestedTrigger, zoneId)
                if (resolved.toLocalDateTime().isAfter(now)) {
                    plans += AlarmPlan(date, key, adhanTime, resolved.toLocalDateTime(), resolved.toInstant().toEpochMilli())
                }
            }
        }
        return AlarmCalculation(plans, missing)
    }

    /** Policy: a repeated local time uses the earlier offset; a nonexistent time moves to the transition's after-time. */
    private fun resolveDst(local: LocalDateTime, zoneId: ZoneId): ZonedDateTime {
        val rules = zoneId.rules
        val offsets = rules.getValidOffsets(local)
        return when {
            offsets.size == 1 -> ZonedDateTime.ofLocal(local, zoneId, offsets[0])
            offsets.size >= 2 -> ZonedDateTime.ofLocal(local, zoneId, offsets[0])
            else -> {
                val transition = rules.getTransition(local)
                ZonedDateTime.ofLocal(transition.dateTimeAfter, zoneId, transition.offsetAfter)
            }
        }
    }
}
