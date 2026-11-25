package com.adhanreminder.app.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import com.adhanreminder.app.BuildConfig
import com.adhanreminder.app.data.AdhanRepository
import com.adhanreminder.app.data.SettingsRepository
import com.adhanreminder.app.data.local.PrayerDay
import com.adhanreminder.app.data.local.ScheduleStatus
import com.adhanreminder.app.data.local.ScheduledAlarm
import com.adhanreminder.app.domain.AlarmCalculator
import com.adhanreminder.app.domain.PrayerTimes24
import com.adhanreminder.app.domain.ScheduleConflictReport
import com.adhanreminder.app.domain.ScheduleConflictResolver
import com.adhanreminder.app.domain.ScheduleDaySnapshot
import com.adhanreminder.app.domain.ValidatedDay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

class SchedulingEngine(
    private val context: Context,
    private val repository: AdhanRepository,
    private val settings: SettingsRepository,
) {
    private val alarmManager = context.getSystemService(AlarmManager::class.java)

    suspend fun rescheduleAll(): Int = globalMutex.withLock {
        cancelStoredAlarms()
        val schedule = repository.getLatestApprovedSchedule()
        if (schedule == null) {
            RescanReminder.cancel(context)
            return@withLock 0
        }
        val days = repository.getPrayerDays(schedule.id)
        val count = scheduleDays(schedule.id, days)
        RescanReminder.schedule(context, repository, settings, schedule.id, days)
        if (BuildConfig.DEBUG) android.util.Log.d(TAG, "rescheduled count=$count")
        count
    }

    suspend fun rescheduleDay(date: String): Int = globalMutex.withLock {
        val schedule = repository.getLatestApprovedSchedule() ?: return@withLock 0
        repository.getAlarms(schedule.id).filter { it.gregorianDate == date }.forEach(::cancelAlarm)
        repository.deleteAlarmsForDate(schedule.id, date)
        val day = repository.getPrayerDays(schedule.id).firstOrNull { it.gregorianDate == date } ?: return@withLock 0
        scheduleDays(schedule.id, listOf(day))
    }

    suspend fun inspectApprovalConflicts(newScheduleId: Long): ScheduleConflictReport? {
        val existing = repository.getLatestApprovedSchedule() ?: return null
        if (existing.id == newScheduleId) return null
        val existingDays = repository.getPrayerDays(existing.id).map { it.toSnapshot() }
        val newDays = repository.getPrayerDays(newScheduleId).map { it.toSnapshot() }
        val report = ScheduleConflictResolver.detectConflicts(
            existingScheduleId = existing.id,
            existingScheduleLabel = existing.hijriMonthLabel.ifBlank { "الجدول السابق" },
            existingDays = existingDays,
            newDays = newDays,
        )
        return report.takeIf { it.hasConflicts }
    }

    suspend fun approveWithMerge(newScheduleId: Long, existingScheduleId: Long): Int = globalMutex.withLock {
        cancelStoredAlarms()
        RescanReminder.cancel(context)

        val existingDays = repository.getPrayerDays(existingScheduleId).map { it.toSnapshot() }
        val newDays = repository.getPrayerDays(newScheduleId).map { it.toSnapshot() }
        val mergedSnapshots = ScheduleConflictResolver.mergeDays(existingDays, newDays)

        val mergedEntities = mergedSnapshots.map { snapshot ->
            PrayerDay(
                scheduleId = newScheduleId,
                gregorianDate = snapshot.gregorianDate,
                hijriDay = snapshot.hijriDay,
                fajr = snapshot.fajr,
                dhuhr = snapshot.dhuhr,
                asr = snapshot.asr,
                maghrib = snapshot.maghrib,
                isha = snapshot.isha,
                reviewFlagsJson = "[]",
            )
        }

        repository.deletePrayerDays(newScheduleId)
        repository.insertPrayerDays(mergedEntities)

        repository.demoteAllApprovedSchedules()
        val schedule = repository.getSchedule(newScheduleId) ?: return@withLock 0
        repository.updateSchedule(schedule.copy(status = ScheduleStatus.APPROVED))
        val finalDays = repository.getPrayerDays(newScheduleId)
        val count = scheduleDays(newScheduleId, finalDays)
        RescanReminder.schedule(context, repository, settings, newScheduleId, finalDays)
        count
    }

    suspend fun approveSchedule(scheduleId: Long): Int = globalMutex.withLock {
        cancelStoredAlarms()
        RescanReminder.cancel(context)
        repository.demoteAllApprovedSchedules()
        val schedule = repository.getSchedule(scheduleId) ?: return@withLock 0
        repository.updateSchedule(schedule.copy(status = ScheduleStatus.APPROVED))
        val days = repository.getPrayerDays(scheduleId)
        val count = scheduleDays(scheduleId, days)
        RescanReminder.schedule(context, repository, settings, scheduleId, days)
        count
    }

    suspend fun cancelAll() = globalMutex.withLock {
        cancelStoredAlarms()
        RescanReminder.cancel(context)
        repository.deleteAllData()
    }

    suspend fun countFutureAlarms(scheduleId: Long): Int =
        repository.getPendingAlarms(scheduleId).count { it.triggerAtMillis > System.currentTimeMillis() }

    private suspend fun cancelStoredAlarms() {
        repository.getAllAlarms().forEach(::cancelAlarm)
        repository.deleteAllAlarms()
    }

    private suspend fun scheduleDays(scheduleId: Long, days: List<PrayerDay>): Int {
        val offset = settings.reminderOffsetMinutes.first()
        val zone = ZoneId.systemDefault()
        val now = nowProvider(zone)
        var count = 0
        days.forEach { day ->
            val calculation = AlarmCalculator.calculate(day.asValidated(), offset, zone, now)
            calculation.plans.forEach { plan ->
                val epochDay = runCatching { LocalDate.parse(day.gregorianDate).toEpochDay().toInt().coerceAtLeast(1) }.getOrDefault(1)
                val requestCode = epochDay * 10 + plan.prayerKey.ordinal
                val row = ScheduledAlarm(
                    scheduleId = scheduleId,
                    prayerKey = plan.prayerKey,
                    gregorianDate = day.gregorianDate,
                    triggerAtMillis = plan.triggerAtMillis,
                    requestCode = requestCode,
                )
                val alarmId = repository.insertAlarm(row)
                scheduleAlarm(row.copy(id = alarmId))
                count++
            }
        }
        return count
    }

    private fun alarmPendingIntent(alarm: ScheduledAlarm): PendingIntent =
        PendingIntent.getBroadcast(
            context,
            alarm.requestCode,
            Intent(context, AlarmReceiver::class.java).apply {
                putExtra(AlarmReceiver.EXTRA_PRAYER_KEY, alarm.prayerKey.name)
                putExtra(AlarmReceiver.EXTRA_DATE, alarm.gregorianDate)
                putExtra(AlarmReceiver.EXTRA_ALARM_ID, alarm.id)
                putExtra(AlarmReceiver.EXTRA_REQUEST_CODE, alarm.requestCode)
            },
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    @SuppressLint("MissingPermission")
    private fun scheduleAlarm(alarm: ScheduledAlarm) {
        val operation = alarmPendingIntent(alarm)
        val show = PendingIntent.getActivity(
            context,
            alarm.requestCode,
            Intent(context, com.adhanreminder.app.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        scheduleExactOrFallback(alarmManager, alarm.triggerAtMillis, show, operation)
    }

    private fun cancelAlarm(alarm: ScheduledAlarm) {
        val operation = alarmPendingIntent(alarm)
        alarmManager.cancel(operation)
        operation.cancel()
    }

    private fun PrayerDay.asValidated() = ValidatedDay(
        date = runCatching { LocalDate.parse(gregorianDate) }.getOrNull(),
        gregorianDate = gregorianDate,
        hijriDay = hijriDay,
        times = PrayerTimes24(fajr, dhuhr, asr, maghrib, isha),
        flags = mutableListOf(),
    )

    private fun PrayerDay.toSnapshot() = ScheduleDaySnapshot(
        gregorianDate = gregorianDate,
        hijriDay = hijriDay,
        fajr = fajr,
        dhuhr = dhuhr,
        asr = asr,
        maghrib = maghrib,
        isha = isha,
    )

    companion object {
        private const val TAG = "SchedulingEngine"
        private val globalMutex = Mutex()
        internal var nowProvider: (ZoneId) -> LocalDateTime = { LocalDateTime.now(it) }

        fun canScheduleExactAlarms(context: Context): Boolean {
            val manager = context.getSystemService(AlarmManager::class.java) ?: return false
            return manager.canScheduleExactAlarms()
        }

        @SuppressLint("MissingPermission")
        fun scheduleExactOrFallback(
            alarmManager: AlarmManager,
            triggerAtMillis: Long,
            showIntent: PendingIntent,
            operation: PendingIntent,
        ) {
            if (alarmManager.canScheduleExactAlarms()) {
                try {
                    alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(triggerAtMillis, showIntent), operation)
                    return
                } catch (e: SecurityException) {
                    if (BuildConfig.DEBUG) android.util.Log.w(TAG, "Exact alarm denied, falling back", e)
                }
            }
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAtMillis, operation)
        }
    }
}
