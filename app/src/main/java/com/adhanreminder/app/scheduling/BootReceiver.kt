package com.adhanreminder.app.scheduling

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.adhanreminder.app.AdhanApplication
import com.adhanreminder.app.update.UpdateScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val supported = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            android.app.AlarmManager.ACTION_SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED,
        )
        if (intent.action !in supported) return
        UpdateScheduler.start(context.applicationContext)
        val pending = goAsync()
        val app = context.applicationContext as AdhanApplication
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val count = SchedulingEngine(context.applicationContext, app.repository, app.settings).rescheduleAll()
                if (com.adhanreminder.app.BuildConfig.DEBUG) android.util.Log.d("BootReceiver", "rescheduled after ${intent.action}, count=$count")
            } finally {
                pending.finish()
            }
        }
    }
}
