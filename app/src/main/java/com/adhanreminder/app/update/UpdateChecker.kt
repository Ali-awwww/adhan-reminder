package com.adhanreminder.app.update

import android.content.Context
import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.adhanreminder.app.AdhanApplication
import com.adhanreminder.app.BuildConfig
import com.adhanreminder.app.R
import com.adhanreminder.app.scheduling.NotificationChannels
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

object UpdateChecker {
    private const val API_URL = "https://api.github.com/repos/Ali-awwww/adhan-reminder/releases/latest"
    private const val USER_AGENT = "AdhanReminder/${BuildConfig.VERSION_NAME}"
    private const val CONNECT_TIMEOUT_MS = 10_000
    private const val READ_TIMEOUT_MS = 15_000
    private const val MAX_RESPONSE_BYTES = 512 * 1024
    private const val NOTIFICATION_ID = 700001

    @SuppressLint("MissingPermission")
    suspend fun check(context: Context) = withContext(Dispatchers.IO) {
        val app = context.applicationContext as AdhanApplication
        val release = fetchLatestRelease() ?: return@withContext
        if (release.draft || release.prerelease || !isNewer(release.tagName, BuildConfig.VERSION_NAME)) return@withContext
        if (app.settings.lastNotifiedReleaseTag.firstValue() == release.tagName) return@withContext
        if (!NotificationChannels.canPost(context)) return@withContext

        NotificationChannels.ensureUpdate(context)
        val openRelease = PendingIntentFactory.releasePage(context, release.htmlUrl)
        val notification = NotificationCompat.Builder(context, NotificationChannels.UPDATE_ID)
            .setSmallIcon(R.drawable.ic_launcher)
            .setContentTitle("يتوفر إصدار جديد من مذكّر الأذان")
            .setContentText("الإصدار ${release.tagName} متاح للتنزيل من GitHub")
            .setStyle(NotificationCompat.BigTextStyle().bigText("الإصدار ${release.tagName} متاح. اضغط لفتح صفحة الإصدار والتنزيل من GitHub."))
            .setContentIntent(openRelease)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
        app.settings.setLastNotifiedReleaseTag(release.tagName)
    }

    private fun fetchLatestRelease(): Release? {
        val connection = (URL(API_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("User-Agent", USER_AGENT)
        }
        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return null
            val bytes = connection.inputStream.use { stream ->
                val buffer = ByteArray(8192)
                val output = java.io.ByteArrayOutputStream()
                var total = 0
                while (true) {
                    val read = stream.read(buffer)
                    if (read == -1) break
                    total += read
                    if (total > MAX_RESPONSE_BYTES) return null
                    output.write(buffer, 0, read)
                }
                output.toByteArray()
            }
            val json = JSONObject(bytes.toString(Charsets.UTF_8))
            Release(
                tagName = json.optString("tag_name").trim(),
                htmlUrl = json.optString("html_url").trim(),
                draft = json.optBoolean("draft"),
                prerelease = json.optBoolean("prerelease"),
            ).takeIf { it.tagName.isNotBlank() && it.htmlUrl.startsWith("https://github.com/") }
        } catch (_: Exception) {
            null
        } finally {
            connection.disconnect()
        }
    }

    private fun isNewer(candidate: String, current: String): Boolean {
        val candidateParts = parseVersion(candidate) ?: return false
        val currentParts = parseVersion(current) ?: return false
        for (index in candidateParts.indices) {
            if (candidateParts[index] != currentParts[index]) return candidateParts[index] > currentParts[index]
        }
        return false
    }

    private fun parseVersion(value: String): List<Int>? {
        val normalized = value.trim().removePrefix("v").substringBefore("+").substringBefore("-")
        if (normalized.isBlank()) return null
        val parts = normalized.split(".")
        if (parts.size !in 1..4 || parts.any { it.isBlank() || it.toIntOrNull() == null }) return null
        return parts.map { it.toInt() }.let { it + List(4 - it.size) { 0 } }
    }

    private data class Release(val tagName: String, val htmlUrl: String, val draft: Boolean, val prerelease: Boolean)
}

private object PendingIntentFactory {
    fun releasePage(context: Context, url: String): android.app.PendingIntent {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return android.app.PendingIntent.getActivity(
            context,
            700001,
            intent,
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
    }
}

private suspend fun kotlinx.coroutines.flow.Flow<String?>.firstValue(): String? = first()
