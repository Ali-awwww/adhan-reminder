package com.adhanreminder.app.scheduling

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.net.Uri
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

object NotificationChannels {
    const val RESCAN_ID = "rescan_reminder"
    const val UPDATE_ID = "app_updates"
    private const val RING_PREFIX = "adhan_ring_v"

    fun ensureRescan(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(RESCAN_ID) == null) {
            manager.createNotificationChannel(NotificationChannel(RESCAN_ID, "تذكير انتهاء الجدول", NotificationManager.IMPORTANCE_DEFAULT))
        }
    }

    fun ensureUpdate(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        if (manager.getNotificationChannel(UPDATE_ID) == null) {
            manager.createNotificationChannel(NotificationChannel(UPDATE_ID, "تحديثات التطبيق", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "إشعارات إصدارات مذكّر الأذان الجديدة من GitHub"
                lockscreenVisibility = android.app.Notification.VISIBILITY_PRIVATE
            })
        }
    }

    fun ensureRing(context: Context, version: Int = 1, sound: Uri? = null, vibrate: Boolean = true): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        val id = "$RING_PREFIX$version"
        if (manager.getNotificationChannel(id) == null) {
            val channel = NotificationChannel(id, "منبهات الأذان", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "تنبيهات الأذان"
                setSound(sound ?: Settings.System.DEFAULT_ALARM_ALERT_URI, AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                enableVibration(vibrate)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            manager.createNotificationChannel(channel)
        }
        return id
    }

    fun recreateRing(context: Context, version: Int, sound: Uri?, vibrate: Boolean): String {
        val manager = context.getSystemService(NotificationManager::class.java)
        val id = ensureRing(context, version, sound, vibrate)
        manager.notificationChannels.filter { it.id.startsWith(RING_PREFIX) && it.id != id }.forEach { manager.deleteNotificationChannel(it.id) }
        return id
    }

    fun canPost(context: Context): Boolean = NotificationManagerCompat.from(context).areNotificationsEnabled()
}
