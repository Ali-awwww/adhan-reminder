package com.adhanreminder.app.update

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

object UpdateScheduler {
    private const val REQUEST_CODE = 700002
    private const val ACTION = "com.adhanreminder.app.CHECK_GITHUB_UPDATE"
    private const val INTERVAL_MILLIS = 2 * 60 * 60 * 1000L

    fun start(context: Context) {
        val alarmManager = context.getSystemService(AlarmManager::class.java)
        val operation = pendingIntent(context)
        alarmManager.cancel(operation)
        alarmManager.setInexactRepeating(
            AlarmManager.ELAPSED_REALTIME,
            android.os.SystemClock.elapsedRealtime() + INTERVAL_MILLIS,
            INTERVAL_MILLIS,
            operation,
        )
    }

    fun stop(context: Context) {
        val operation = pendingIntent(context)
        context.getSystemService(AlarmManager::class.java).cancel(operation)
        operation.cancel()
    }

    private fun pendingIntent(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, UpdateReceiver::class.java).setAction(ACTION),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}

class UpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != "com.adhanreminder.app.CHECK_GITHUB_UPDATE") return
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                UpdateChecker.check(context.applicationContext)
            } finally {
                pending.finish()
            }
        }
    }
}
