package com.adhanreminder.app.data

import android.content.Context
import android.util.Base64
import com.adhanreminder.app.data.local.MonthSchedule
import com.adhanreminder.app.data.local.PrayerDay
import org.json.JSONArray
import org.json.JSONObject
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class ScheduleBackupManager(private val context: Context) {
    private val alias = "adhan_schedule_backup_key"

    fun export(schedule: MonthSchedule, days: List<PrayerDay>, output: OutputStream) {
        val plaintext = JSONObject().apply {
            put("format", "adhan-reminder-backup-v1")
            put("schedule", JSONObject().apply { put("hijriMonthLabel", schedule.hijriMonthLabel); put("createdAtMillis", schedule.createdAtMillis) })
            put("days", JSONArray(days.map { day -> JSONObject().apply { put("gregorianDate", day.gregorianDate); put("hijriDay", day.hijriDay); put("fajr", day.fajr); put("dhuhr", day.dhuhr); put("asr", day.asr); put("maghrib", day.maghrib); put("isha", day.isha); put("reviewFlagsJson", day.reviewFlagsJson) } }))
        }.toString().toByteArray(Charsets.UTF_8)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key())
        val encrypted = cipher.doFinal(plaintext)
        val envelope = JSONObject().put("format", "adhan-reminder-encrypted-v1").put("iv", Base64.encodeToString(cipher.iv, Base64.NO_WRAP)).put("data", Base64.encodeToString(encrypted, Base64.NO_WRAP))
        output.use { it.write(envelope.toString().toByteArray(Charsets.UTF_8)) }
    }

    fun import(input: InputStream): ImportedSchedule {
        val envelope = JSONObject(input.use { it.readBytes().toString(Charsets.UTF_8) })
        require(envelope.optString("format") == "adhan-reminder-encrypted-v1") { "نسخة احتياطية غير مدعومة" }
        val iv = Base64.decode(envelope.getString("iv"), Base64.DEFAULT)
        val encrypted = Base64.decode(envelope.getString("data"), Base64.DEFAULT)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, iv))
        val root = JSONObject(cipher.doFinal(encrypted).toString(Charsets.UTF_8))
        require(root.optString("format") == "adhan-reminder-backup-v1") { "بيانات النسخة غير صالحة" }
        val schedule = root.getJSONObject("schedule")
        val daysJson = root.getJSONArray("days")
        val days = (0 until daysJson.length()).map { i -> daysJson.getJSONObject(i) }.map { day -> BackupDay(day.getString("gregorianDate"), if (day.isNull("hijriDay")) null else day.optInt("hijriDay"), day.getString("fajr"), day.getString("dhuhr"), day.getString("asr"), day.getString("maghrib"), day.getString("isha"), day.optString("reviewFlagsJson", "[]")) }
        return ImportedSchedule(schedule.optString("hijriMonthLabel", "نسخة مستوردة"), schedule.optLong("createdAtMillis", System.currentTimeMillis()), days)
    }

    private fun key(): SecretKey {
        val store = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (store.getKey(alias, null) as? SecretKey)?.let { return it }
        val generator = KeyGenerator.getInstance("AES", "AndroidKeyStore")
        generator.init(android.security.keystore.KeyGenParameterSpec.Builder(alias, android.security.keystore.KeyProperties.PURPOSE_ENCRYPT or android.security.keystore.KeyProperties.PURPOSE_DECRYPT).setBlockModes(android.security.keystore.KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(android.security.keystore.KeyProperties.ENCRYPTION_PADDING_NONE).build())
        return generator.generateKey()
    }
}

data class BackupDay(val gregorianDate: String, val hijriDay: Int?, val fajr: String, val dhuhr: String, val asr: String, val maghrib: String, val isha: String, val reviewFlagsJson: String)
data class ImportedSchedule(val hijriMonthLabel: String, val createdAtMillis: Long, val days: List<BackupDay>)
