package com.adhanreminder.app.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import com.adhanreminder.app.BuildConfig
import com.adhanreminder.app.data.AdhanRepository
import com.adhanreminder.app.data.SettingsRepository
import com.adhanreminder.app.data.local.PrayerDay
import com.adhanreminder.app.data.local.ScheduledAlarm
import com.adhanreminder.app.domain.AlarmCalculator
import com.adhanreminder.app.domain.PrayerTimes24
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

    suspend fun approveSchedule(scheduleId: Long): Int = globalMutex.withLock {
        cancelStoredAlarms()
        RescanReminder.cancel(context)
        repository.demoteAllApprovedSchedules()
        val schedule = repository.getSchedule(scheduleId) ?: return@withLock 0
        repository.updateSchedule(schedule.copy(status = com.adhanreminder.app.data.local.ScheduleStatus.APPROVED))
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
        val gap = settings.fajrGapMinutes.first()
        val zone = ZoneId.systemDefault()
        val now = LocalDateTime.now(zone)
        var count = 0
        days.forEach { day ->
            val calculation = AlarmCalculator.calculate(day.asValidated(), offset, gap, zone, now)
            calculation.plans.forEach { plan ->
                val dayIndex = runCatching { LocalDate.parse(day.gregorianDate).dayOfMonth }.getOrDefault(1)
                val requestCode = dayIndex * 10 + plan.prayerKey.ordinal
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

    private fun scheduleAlarm(alarm: ScheduledAlarm) {
        val operation = alarmPendingIntent(alarm)
        val show = PendingIntent.getActivity(
            context,
            alarm.requestCode,
            Intent(context, com.adhanreminder.app.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        alarmManager.setAlarmClock(AlarmManager.AlarmClockInfo(alarm.triggerAtMillis, show), operation)
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

    companion object {
        private const val TAG = "SchedulingEngine"
        private val globalMutex = Mutex()
    }
}
