package com.adhanreminder.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.adhanreminder.app.domain.ScanQuotaPolicy
import com.adhanreminder.app.domain.ScanQuotaRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val reminderOffsetMinutes = intPreferencesKey("reminderOffsetMinutes")
        val fajrGapMinutes = intPreferencesKey("fajrGapMinutes")
        val rescanReminderDays = intPreferencesKey("rescanReminderDays")
        val alarmVibrate = booleanPreferencesKey("alarmVibrate")
        val darkTheme = booleanPreferencesKey("darkTheme")
        val disclaimerAccepted = booleanPreferencesKey("disclaimerAccepted")
        val lastTestFiredMillis = longPreferencesKey("lastTestFiredMillis")
        val alarmSoundUri = stringPreferencesKey("alarmSoundUri")
        val ringChannelVersion = intPreferencesKey("ringChannelVersion")
        val ringFullScreenBlocked = booleanPreferencesKey("ringFullScreenBlocked")
        val onboardingCompleted = booleanPreferencesKey("onboardingCompleted")
        val demoMode = booleanPreferencesKey("demoMode")
        val lastTestScheduledMillis = longPreferencesKey("lastTestScheduledMillis")
        val lastNotifiedReleaseTag = stringPreferencesKey("lastNotifiedReleaseTag")
        val scanDayKey = stringPreferencesKey("scanDayKey")
        val scanDailyCount = intPreferencesKey("scanDailyCount")
        val scanMonthKey = stringPreferencesKey("scanMonthKey")
        val scanMonthlyCount = intPreferencesKey("scanMonthlyCount")
        val lastScanAttemptMillis = longPreferencesKey("lastScanAttemptMillis")
        val pendingFajrNoteScheduleId = longPreferencesKey("pendingFajrNoteScheduleId")
        val pendingFajrNoteMinutes = intPreferencesKey("pendingFajrNoteMinutes")
    }

    val reminderOffsetMinutes: Flow<Int> = context.settingsDataStore.data.map { it[Keys.reminderOffsetMinutes]?.takeIf { value -> value in 1..60 } ?: 10 }
    val fajrGapMinutes: Flow<Int> = context.settingsDataStore.data.map { it[Keys.fajrGapMinutes]?.takeIf { value -> value in 0..60 } ?: 20 }
    val rescanReminderDays: Flow<Int> = context.settingsDataStore.data.map { it[Keys.rescanReminderDays]?.takeIf { value -> value in 1..7 } ?: 3 }
    val alarmVibrate: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.alarmVibrate] ?: true }
    val darkTheme: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.darkTheme] ?: true }
    val disclaimerAccepted: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.disclaimerAccepted] ?: false }
    val lastTestFiredMillis: Flow<Long?> = context.settingsDataStore.data.map { it[Keys.lastTestFiredMillis] }
    val alarmSoundUri: Flow<String?> = context.settingsDataStore.data.map { it[Keys.alarmSoundUri] }
    val ringChannelVersion: Flow<Int> = context.settingsDataStore.data.map { it[Keys.ringChannelVersion]?.takeIf { value -> value > 0 } ?: 1 }
    val ringFullScreenBlocked: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.ringFullScreenBlocked] ?: false }
    val onboardingCompleted: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.onboardingCompleted] ?: false }
    val demoMode: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.demoMode] ?: false }
    val lastTestScheduledMillis: Flow<Long?> = context.settingsDataStore.data.map { it[Keys.lastTestScheduledMillis] }
    val lastNotifiedReleaseTag: Flow<String?> = context.settingsDataStore.data.map { it[Keys.lastNotifiedReleaseTag] }

    val scanQuotaRecord: Flow<ScanQuotaRecord> = context.settingsDataStore.data.map { prefs ->
        ScanQuotaRecord(
            dayKey = prefs[Keys.scanDayKey].orEmpty(),
            dailyCount = prefs[Keys.scanDailyCount] ?: 0,
            monthKey = prefs[Keys.scanMonthKey].orEmpty(),
            monthlyCount = prefs[Keys.scanMonthlyCount] ?: 0,
            lastAttemptMillis = prefs[Keys.lastScanAttemptMillis] ?: 0L,
        )
    }

    val pendingFajrNote: Flow<Pair<Long, Int>?> = context.settingsDataStore.data.map { prefs ->
        val scheduleId = prefs[Keys.pendingFajrNoteScheduleId]
        val minutes = prefs[Keys.pendingFajrNoteMinutes]?.takeIf { it in 0..60 }
        if (scheduleId != null && minutes != null) scheduleId to minutes else null
    }

    suspend fun setReminderOffsetMinutes(value: Int) { requireInRange(value, 1, 60, "reminderOffsetMinutes"); edit { it[Keys.reminderOffsetMinutes] = value } }
    suspend fun setFajrGapMinutes(value: Int) { requireInRange(value, 0, 60, "fajrGapMinutes"); edit { it[Keys.fajrGapMinutes] = value } }
    suspend fun setRescanReminderDays(value: Int) { requireInRange(value, 1, 7, "rescanReminderDays"); edit { it[Keys.rescanReminderDays] = value } }
    suspend fun setAlarmVibrate(value: Boolean) { edit { it[Keys.alarmVibrate] = value } }
    suspend fun setDarkTheme(value: Boolean) { edit { it[Keys.darkTheme] = value } }
    suspend fun setDisclaimerAccepted(value: Boolean) { edit { it[Keys.disclaimerAccepted] = value } }
    suspend fun setLastTestFiredMillis(value: Long?) { edit { if (value == null) it.remove(Keys.lastTestFiredMillis) else it[Keys.lastTestFiredMillis] = value } }
    suspend fun setAlarmSoundUri(value: String?) { edit { if (value == null) it.remove(Keys.alarmSoundUri) else it[Keys.alarmSoundUri] = value } }
    suspend fun bumpRingChannelVersion(): Int { var next = 1; context.settingsDataStore.edit { next = (it[Keys.ringChannelVersion]?.takeIf { value -> value > 0 } ?: 1) + 1; it[Keys.ringChannelVersion] = next }; return next }
    suspend fun setRingFullScreenBlocked(value: Boolean) { edit { it[Keys.ringFullScreenBlocked] = value } }
    suspend fun setOnboardingCompleted(value: Boolean) { edit { it[Keys.onboardingCompleted] = value } }
    suspend fun setDemoMode(value: Boolean) { edit { it[Keys.demoMode] = value } }
    suspend fun setLastTestScheduledMillis(value: Long?) { edit { if (value == null) it.remove(Keys.lastTestScheduledMillis) else it[Keys.lastTestScheduledMillis] = value } }
    suspend fun setLastNotifiedReleaseTag(value: String?) { edit { if (value == null) it.remove(Keys.lastNotifiedReleaseTag) else it[Keys.lastNotifiedReleaseTag] = value } }

    suspend fun recordScanAttempt(nowMillis: Long, today: LocalDate = LocalDate.now()) {
        edit { prefs ->
            val current = ScanQuotaRecord(
                dayKey = prefs[Keys.scanDayKey].orEmpty(),
                dailyCount = prefs[Keys.scanDailyCount] ?: 0,
                monthKey = prefs[Keys.scanMonthKey].orEmpty(),
                monthlyCount = prefs[Keys.scanMonthlyCount] ?: 0,
                lastAttemptMillis = prefs[Keys.lastScanAttemptMillis] ?: 0L,
            )
            val updated = ScanQuotaPolicy.recordAttempt(current, nowMillis, today)
            prefs[Keys.scanDayKey] = updated.dayKey
            prefs[Keys.scanDailyCount] = updated.dailyCount
            prefs[Keys.scanMonthKey] = updated.monthKey
            prefs[Keys.scanMonthlyCount] = updated.monthlyCount
            prefs[Keys.lastScanAttemptMillis] = updated.lastAttemptMillis
        }
    }

    suspend fun setPendingFajrNote(scheduleId: Long, minutes: Int) {
        requireInRange(minutes, 0, 60, "pendingFajrNoteMinutes")
        edit { prefs ->
            prefs[Keys.pendingFajrNoteScheduleId] = scheduleId
            prefs[Keys.pendingFajrNoteMinutes] = minutes
        }
    }

    suspend fun clearPendingFajrNote() {
        edit { prefs ->
            prefs.remove(Keys.pendingFajrNoteScheduleId)
            prefs.remove(Keys.pendingFajrNoteMinutes)
        }
    }

    suspend fun clearUserData() {
        edit { preferences ->
            preferences.remove(Keys.disclaimerAccepted)
            preferences.remove(Keys.lastTestFiredMillis)
            preferences.remove(Keys.lastTestScheduledMillis)
            preferences.remove(Keys.alarmSoundUri)
            preferences.remove(Keys.ringChannelVersion)
            preferences.remove(Keys.ringFullScreenBlocked)
            preferences.remove(Keys.pendingFajrNoteScheduleId)
            preferences.remove(Keys.pendingFajrNoteMinutes)
        }
    }

    suspend fun resetForFullClear() { context.settingsDataStore.edit { it.clear() } }

    private suspend fun edit(block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) { context.settingsDataStore.edit(block) }
    private fun requireInRange(value: Int, min: Int, max: Int, name: String) { require(value in min..max) { "$name must be between $min and $max" } }
}
