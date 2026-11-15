package com.adhanreminder.app.scheduling

import android.app.AlarmManager
import android.app.PendingIntent
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.adhanreminder.app.AdhanApplication
import com.adhanreminder.app.R
import com.adhanreminder.app.data.AdhanRepository
import com.adhanreminder.app.data.SettingsRepository
import com.adhanreminder.app.data.local.PrayerDay
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

object RescanReminder {
    private const val REQUEST_CODE = 900001
    const val ACTION = "com.adhanreminder.app.RESCAN_REMINDER"

    @SuppressLint("MissingPermission")
    suspend fun schedule(context: Context, repository: AdhanRepository, settings: SettingsRepository, scheduleId: Long, days: List<PrayerDay>) {
        cancel(context)
        val last = days.mapNotNull { runCatching { LocalDate.parse(it.gregorianDate) }.getOrNull() }.maxOrNull() ?: return
        val reminderDays = settings.rescanReminderDays.first()
        val date = last.minusDays(reminderDays.toLong())
        val trigger = LocalDateTime.of(date, LocalTime.of(9, 0)).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        if (trigger <= System.currentTimeMillis()) return
        NotificationChannels.ensureRescan(context)
        val operation = PendingIntent.getBroadcast(context, REQUEST_CODE, Intent(context, RescanReceiver::class.java).setAction(ACTION), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val show = PendingIntent.getActivity(context, REQUEST_CODE, Intent(context, com.adhanreminder.app.MainActivity::class.java).putExtra("openCapture", true), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        context.getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(trigger, show), operation)
    }

    fun cancel(context: Context) {
        val operation = PendingIntent.getBroadcast(context, REQUEST_CODE, Intent(context, RescanReceiver::class.java).setAction(ACTION), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        context.getSystemService(AlarmManager::class.java).cancel(operation)
        operation.cancel()
    }
}

class RescanReceiver : android.content.BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        NotificationChannels.ensureRescan(context)
        val show = PendingIntent.getActivity(context, 900001, Intent(context, com.adhanreminder.app.MainActivity::class.java).putExtra("openCapture", true), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val notification = NotificationCompat.Builder(context, NotificationChannels.RESCAN_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("اقترب انتهاء الجدول")
            .setContentText("صوّر الجدول الجديد من التطبيق")
            .setContentIntent(show)
            .setAutoCancel(true)
            .build()
        context.getSystemService(android.app.NotificationManager::class.java).notify(900001, notification)
    }
}
