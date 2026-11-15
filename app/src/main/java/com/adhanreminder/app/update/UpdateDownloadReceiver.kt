package com.adhanreminder.app.update

import android.app.DownloadManager
import android.app.PendingIntent
import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.adhanreminder.app.R
import com.adhanreminder.app.scheduling.NotificationChannels

class UpdateDownloadReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != ACTION) return
        val url = intent.getStringExtra(EXTRA_URL) ?: return
        val tag = intent.getStringExtra(EXTRA_TAG) ?: "latest"
        if (!url.startsWith("https://github.com/") || !url.endsWith(".apk", ignoreCase = true)) return

        val filename = "AdhanReminder-$tag.apk".replace(Regex("[^A-Za-z0-9._-]"), "_")
        val request = DownloadManager.Request(Uri.parse(url)).apply {
            setTitle("تنزيل تحديث مذكّر الأذان")
            setDescription("الإصدار $tag")
            setMimeType("application/vnd.android.package-archive")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(false)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, filename)
        }
        val manager = context.getSystemService(DownloadManager::class.java)
        val downloadId = manager.enqueue(request)
        context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit()
            .putLong(KEY_DOWNLOAD_ID, downloadId)
            .putString(KEY_TAG, tag)
            .apply()
        postStartedNotification(context, tag)
    }

    @SuppressLint("MissingPermission")
    private fun postStartedNotification(context: Context, tag: String) {
        if (!NotificationChannels.canPost(context)) return
        NotificationChannels.ensureUpdate(context)
        val notification = NotificationCompat.Builder(context, NotificationChannels.UPDATE_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("بدأ تنزيل تحديث مذكّر الأذان")
            .setContentText("الإصدار $tag يُنزّل عبر مدير التنزيلات")
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()
        NotificationManagerCompat.from(context).notify(UPDATE_STARTED_NOTIFICATION_ID, notification)
    }

    companion object {
        const val ACTION = "com.adhanreminder.app.DOWNLOAD_GITHUB_UPDATE"
        const val EXTRA_URL = "extra_url"
        const val EXTRA_TAG = "extra_tag"
        const val PREFERENCES = "update_download"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_TAG = "tag"
        const val UPDATE_STARTED_NOTIFICATION_ID = 700002
    }
}

class UpdateDownloadCompleteReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
        val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
        val preferences = context.getSharedPreferences(UpdateDownloadReceiver.PREFERENCES, Context.MODE_PRIVATE)
        if (id != preferences.getLong(UpdateDownloadReceiver.KEY_DOWNLOAD_ID, -1L)) return

        val manager = context.getSystemService(DownloadManager::class.java)
        val query = DownloadManager.Query().setFilterById(id)
        manager.query(query).use { cursor ->
            if (!cursor.moveToFirst()) return
            val status = cursor.getInt(cursor.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
            if (status != DownloadManager.STATUS_SUCCESSFUL) return
        }
        val uri = manager.getUriForDownloadedFile(id) ?: return
        val tag = preferences.getString(UpdateDownloadReceiver.KEY_TAG, "الجديد") ?: "الجديد"
        postCompleteNotification(context, uri, tag)
    }

    @SuppressLint("MissingPermission")
    private fun postCompleteNotification(context: Context, uri: Uri, tag: String) {
        if (!NotificationChannels.canPost(context)) return
        NotificationChannels.ensureUpdate(context)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val installPendingIntent = PendingIntent.getActivity(
            context,
            700004,
            installIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, NotificationChannels.UPDATE_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("اكتمل تنزيل تحديث مذكّر الأذان")
            .setContentText("اضغط لتثبيت الإصدار $tag")
            .setContentIntent(installPendingIntent)
            .setAutoCancel(true)
            .build()
        NotificationManagerCompat.from(context).notify(UPDATE_COMPLETE_NOTIFICATION_ID, notification)
    }

    companion object {
        const val UPDATE_COMPLETE_NOTIFICATION_ID = 700003
    }
}
