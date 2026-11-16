package com.adhanreminder.app.domain

import com.adhanreminder.app.data.local.PrayerKey

data class ScheduleDaySnapshot(
    val gregorianDate: String,
    val hijriDay: Int?,
    val fajr: String,
    val dhuhr: String,
    val asr: String,
    val maghrib: String,
    val isha: String,
)

data class PrayerTimeConflict(
    val gregorianDate: String,
    val prayerKey: PrayerKey,
    val prayerLabelAr: String,
    val existingTime: String,
    val newTime: String,
)

data class ScheduleConflictReport(
    val existingScheduleId: Long,
    val existingScheduleLabel: String,
    val conflicts: List<PrayerTimeConflict>,
    val conflictingDateCount: Int,
    val nonOverlappingOldDayCount: Int,
) {
    val hasConflicts: Boolean get() = conflicts.isNotEmpty()
}

object ScheduleConflictResolver {
    fun detectConflicts(
        existingScheduleId: Long,
        existingScheduleLabel: String,
        existingDays: List<ScheduleDaySnapshot>,
        newDays: List<ScheduleDaySnapshot>,
    ): ScheduleConflictReport {
        val existingByDate = existingDays.associateBy { it.gregorianDate.trim() }
        val newDates = newDays.map { it.gregorianDate.trim() }.toSet()

        val conflicts = mutableListOf<PrayerTimeConflict>()
        newDays.sortedBy { it.gregorianDate.trim() }.forEach { newDay ->
            val date = newDay.gregorianDate.trim()
            val oldDay = existingByDate[date] ?: return@forEach
            val comparisons = listOf(
                Triple(PrayerKey.FAJR, oldDay.fajr.trim(), newDay.fajr.trim()),
                Triple(PrayerKey.DHUHR, oldDay.dhuhr.trim(), newDay.dhuhr.trim()),
                Triple(PrayerKey.ASR, oldDay.asr.trim(), newDay.asr.trim()),
                Triple(PrayerKey.MAGHRIB, oldDay.maghrib.trim(), newDay.maghrib.trim()),
                Triple(PrayerKey.ISHA, oldDay.isha.trim(), newDay.isha.trim()),
            )
            comparisons.forEach { (key, oldTime, newTime) ->
                if (oldTime != newTime) {
                    conflicts += PrayerTimeConflict(
                        gregorianDate = date,
                        prayerKey = key,
                        prayerLabelAr = key.arabicLabel(),
                        existingTime = oldTime.ifBlank { "—" },
                        newTime = newTime.ifBlank { "—" },
                    )
                }
            }
        }

        val conflictingDateCount = conflicts.map { it.gregorianDate }.distinct().size
        val nonOverlappingOldDayCount = existingDays.count { it.gregorianDate.trim() !in newDates }

        return ScheduleConflictReport(
            existingScheduleId = existingScheduleId,
            existingScheduleLabel = existingScheduleLabel,
            conflicts = conflicts,
            conflictingDateCount = conflictingDateCount,
            nonOverlappingOldDayCount = nonOverlappingOldDayCount,
        )
    }

    /**
     * Merges non-overlapping days from [existingDays] with all [newDays],
     * using [newDays] whenever a gregorianDate appears in both schedules.
     */
    fun mergeDays(
        existingDays: List<ScheduleDaySnapshot>,
        newDays: List<ScheduleDaySnapshot>,
    ): List<ScheduleDaySnapshot> {
        val mergedByDate = LinkedHashMap<String, ScheduleDaySnapshot>()
        existingDays.forEach { day ->
            val key = day.gregorianDate.trim()
            if (key.isNotEmpty()) mergedByDate[key] = day.copy(gregorianDate = key)
        }
        newDays.forEach { day ->
            val key = day.gregorianDate.trim()
            if (key.isNotEmpty()) mergedByDate[key] = day.copy(gregorianDate = key)
        }
        return mergedByDate.values.sortedBy { it.gregorianDate }
    }

    private fun PrayerKey.arabicLabel(): String = when (this) {
        PrayerKey.FAJR -> "الفجر"
        PrayerKey.DHUHR -> "الظهر"
        PrayerKey.ASR -> "العصر"
        PrayerKey.MAGHRIB -> "المغرب"
        PrayerKey.ISHA -> "العشاء"
    }
}
