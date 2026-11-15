package com.adhanreminder.app.scheduling

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import com.adhanreminder.app.AdhanApplication
import com.adhanreminder.app.BuildConfig
import com.adhanreminder.app.R
import com.adhanreminder.app.data.local.PrayerKey
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.LocalDate

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        val app = context.applicationContext as AdhanApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (intent.action == ACTION_TEST) {
                    app.settings.setLastTestFiredMillis(System.currentTimeMillis())
                    pending.finish()
                    return@launch
                }
                val prayerKey = intent.getStringExtra(EXTRA_PRAYER_KEY).orEmpty()
                val date = intent.getStringExtra(EXTRA_DATE).orEmpty()
                val requestCode = intent.getIntExtra(EXTRA_REQUEST_CODE, -1)
                val parsedPrayer = runCatching { PrayerKey.valueOf(prayerKey) }.getOrNull()
                val parsedDate = runCatching { LocalDate.parse(date) }.getOrNull()
                if (parsedPrayer == null || parsedDate == null || requestCode < 0) return@launch
                val stored = if (requestCode >= 0) app.repository.getAlarmByRequestCode(requestCode) else null
                if (stored == null || stored.prayerKey != parsedPrayer || stored.gregorianDate != date) return@launch
                app.repository.setAlarmFired(stored.id, true)
                val day = stored?.let { app.repository.getPrayerDay(it.scheduleId, it.gregorianDate) }
                val writtenTime = when (stored?.prayerKey) {
                    PrayerKey.FAJR -> day?.fajr
                    PrayerKey.DHUHR -> day?.dhuhr
                    PrayerKey.ASR -> day?.asr
                    PrayerKey.MAGHRIB -> day?.maghrib
                    PrayerKey.ISHA -> day?.isha
                    null -> null
                }
                val version = app.settings.ringChannelVersion.first()
                val sound = app.settings.alarmSoundUri.first()?.let { android.net.Uri.parse(it) }
                val vibrate = app.settings.alarmVibrate.first()
                val channelId = NotificationChannels.ensureRing(context, version, sound, vibrate)
                val manager = context.getSystemService(android.app.NotificationManager::class.java)
                val canFullScreen = Build.VERSION.SDK_INT < 34 || manager.canUseFullScreenIntent()
                if (!canFullScreen) app.settings.setRingFullScreenBlocked(true)
                if (!NotificationChannels.canPost(context)) {
                    if (BuildConfig.DEBUG) android.util.Log.d(TAG, "notifications disabled; alarm still fired")
                }
                val ringIntent = Intent(context, RingActivity::class.java).apply {
                    putExtra(EXTRA_PRAYER_KEY, prayerKey)
                    putExtra(EXTRA_DATE, date)
                    putExtra(EXTRA_ALARM_ID, stored?.id ?: -1L)
                    putExtra(EXTRA_ADHAN_TIME, writtenTime)
                }
                val fullScreen = PendingIntent.getActivity(context, requestCode, ringIntent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                val notification = NotificationCompat.Builder(context, channelId)
                    .setSmallIcon(R.drawable.ic_launcher)
                    .setContentTitle(prayerName(prayerKey))
                    .setContentText("حان وقت التذكير")
                    .setCategory(NotificationCompat.CATEGORY_ALARM)
                    .setPriority(NotificationCompat.PRIORITY_MAX)
                    .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                    .setAutoCancel(false)
                    .setOngoing(true)
                    .setFullScreenIntent(fullScreen, canFullScreen)
                    .build()
                manager.notify(requestCode, notification)
            } catch (error: Exception) {
                if (BuildConfig.DEBUG) android.util.Log.d(TAG, "receiver error ${error::class.simpleName}")
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_TEST = "com.adhanreminder.app.TEST_ALARM"
        const val EXTRA_PRAYER_KEY = "prayerKey"
        const val EXTRA_DATE = "gregorianDate"
        const val EXTRA_ALARM_ID = "alarmId"
        const val EXTRA_REQUEST_CODE = "requestCode"
        const val EXTRA_ADHAN_TIME = "adhanTime"
        private const val TAG = "AlarmReceiver"

        fun prayerName(value: String): String = when (runCatching { PrayerKey.valueOf(value) }.getOrNull()) {
            PrayerKey.FAJR -> "الفجر"
            PrayerKey.DHUHR -> "الظهر"
            PrayerKey.ASR -> "العصر"
            PrayerKey.MAGHRIB -> "المغرب"
            PrayerKey.ISHA -> "العشاء"
            else -> "الأذان"
        }
    }
}
