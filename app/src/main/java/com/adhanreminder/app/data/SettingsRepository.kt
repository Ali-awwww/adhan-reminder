package com.adhanreminder.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "settings")

class SettingsRepository(private val context: Context) {
    private object Keys {
        val reminderOffsetMinutes = intPreferencesKey("reminderOffsetMinutes")
        val fajrGapMinutes = intPreferencesKey("fajrGapMinutes")
        val rescanReminderDays = intPreferencesKey("rescanReminderDays")
        val alarmVibrate = booleanPreferencesKey("alarmVibrate")
        val darkTheme = booleanPreferencesKey("darkTheme")
        val geminiApiKey = stringPreferencesKey("geminiApiKey")
        val disclaimerAccepted = booleanPreferencesKey("disclaimerAccepted")
        val lastTestFiredMillis = longPreferencesKey("lastTestFiredMillis")
        val alarmSoundUri = stringPreferencesKey("alarmSoundUri")
        val ringChannelVersion = intPreferencesKey("ringChannelVersion")
        val ringFullScreenBlocked = booleanPreferencesKey("ringFullScreenBlocked")
        val apiKeyStatus = stringPreferencesKey("apiKeyStatus")
        val apiKeyReason = stringPreferencesKey("apiKeyReason")
        val apiKeyFormat = stringPreferencesKey("apiKeyFormat")
        val apiKeyModel = stringPreferencesKey("apiKeyModel")
        val apiKeyLastVerifiedMillis = longPreferencesKey("apiKeyLastVerifiedMillis")
        val onboardingCompleted = booleanPreferencesKey("onboardingCompleted")
        val demoMode = booleanPreferencesKey("demoMode")
        val lastTestScheduledMillis = longPreferencesKey("lastTestScheduledMillis")
        val lastNotifiedReleaseTag = stringPreferencesKey("lastNotifiedReleaseTag")
    }

    val reminderOffsetMinutes: Flow<Int> = context.settingsDataStore.data.map { it[Keys.reminderOffsetMinutes]?.takeIf { value -> value in 1..60 } ?: 10 }
    val fajrGapMinutes: Flow<Int> = context.settingsDataStore.data.map { it[Keys.fajrGapMinutes]?.takeIf { value -> value in 0..60 } ?: 20 }
    val rescanReminderDays: Flow<Int> = context.settingsDataStore.data.map { it[Keys.rescanReminderDays]?.takeIf { value -> value in 1..7 } ?: 3 }
    val alarmVibrate: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.alarmVibrate] ?: true }
    val darkTheme: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.darkTheme] ?: true }
    val geminiApiKey: Flow<String> = context.settingsDataStore.data.map { it[Keys.geminiApiKey]?.trim()?.takeIf { value -> value.length <= MAX_API_KEY_LENGTH } ?: "" }
    val disclaimerAccepted: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.disclaimerAccepted] ?: false }
    val lastTestFiredMillis: Flow<Long?> = context.settingsDataStore.data.map { it[Keys.lastTestFiredMillis] }
    val alarmSoundUri: Flow<String?> = context.settingsDataStore.data.map { it[Keys.alarmSoundUri] }
    val ringChannelVersion: Flow<Int> = context.settingsDataStore.data.map { it[Keys.ringChannelVersion]?.takeIf { value -> value > 0 } ?: 1 }
    val ringFullScreenBlocked: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.ringFullScreenBlocked] ?: false }
    val apiKeyStatus: Flow<String> = context.settingsDataStore.data.map { it[Keys.apiKeyStatus].orEmpty().takeIf { value -> value in API_KEY_STATUSES } ?: "NOT_SET" }
    val apiKeyReason: Flow<String> = context.settingsDataStore.data.map { it[Keys.apiKeyReason].orEmpty() }
    val apiKeyFormat: Flow<String> = context.settingsDataStore.data.map { it[Keys.apiKeyFormat].orEmpty().takeIf { value -> value in API_KEY_FORMATS } ?: "UNKNOWN" }
    val apiKeyModel: Flow<String> = context.settingsDataStore.data.map { it[Keys.apiKeyModel].orEmpty() }
    val apiKeyLastVerifiedMillis: Flow<Long?> = context.settingsDataStore.data.map { it[Keys.apiKeyLastVerifiedMillis] }
    val onboardingCompleted: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.onboardingCompleted] ?: false }
    val demoMode: Flow<Boolean> = context.settingsDataStore.data.map { it[Keys.demoMode] ?: false }
    val lastTestScheduledMillis: Flow<Long?> = context.settingsDataStore.data.map { it[Keys.lastTestScheduledMillis] }
    val lastNotifiedReleaseTag: Flow<String?> = context.settingsDataStore.data.map { it[Keys.lastNotifiedReleaseTag] }

    suspend fun setReminderOffsetMinutes(value: Int) { requireInRange(value, 1, 60, "reminderOffsetMinutes"); edit { it[Keys.reminderOffsetMinutes] = value } }
    suspend fun setFajrGapMinutes(value: Int) { requireInRange(value, 0, 60, "fajrGapMinutes"); edit { it[Keys.fajrGapMinutes] = value } }
    suspend fun setRescanReminderDays(value: Int) { requireInRange(value, 1, 7, "rescanReminderDays"); edit { it[Keys.rescanReminderDays] = value } }
    suspend fun setAlarmVibrate(value: Boolean) { edit { it[Keys.alarmVibrate] = value } }
    suspend fun setDarkTheme(value: Boolean) { edit { it[Keys.darkTheme] = value } }
    suspend fun setGeminiApiKey(value: String) {
        val trimmed = value.trim()
        require(trimmed.length <= MAX_API_KEY_LENGTH) { "API key is too long" }
        edit { if (trimmed.isBlank()) it.remove(Keys.geminiApiKey) else it[Keys.geminiApiKey] = trimmed }
    }
    suspend fun saveVerifiedApiKey(key: String, status: String, reason: String, format: String, model: String, verifiedAtMillis: Long) {
        val trimmed = key.trim()
        require(trimmed.isNotEmpty() && trimmed.length <= MAX_API_KEY_LENGTH)
        require(status == "OK_READY" || status == "QUOTA_LIMITED")
        edit {
            it[Keys.geminiApiKey] = trimmed
            it[Keys.apiKeyStatus] = status
            it[Keys.apiKeyReason] = reason
            it[Keys.apiKeyFormat] = format
            it[Keys.apiKeyModel] = model
            it[Keys.apiKeyLastVerifiedMillis] = verifiedAtMillis
        }
    }
    suspend fun setApiKeyFailureForScan(reason: String) { edit { it[Keys.apiKeyStatus] = "FAILED"; it[Keys.apiKeyReason] = reason } }
    suspend fun setApiKeyQuotaLimited() { edit { it[Keys.apiKeyStatus] = "QUOTA_LIMITED"; it[Keys.apiKeyReason] = "QUOTA_LIMITED" } }
    suspend fun setDisclaimerAccepted(value: Boolean) { edit { it[Keys.disclaimerAccepted] = value } }
    suspend fun setLastTestFiredMillis(value: Long?) { edit { if (value == null) it.remove(Keys.lastTestFiredMillis) else it[Keys.lastTestFiredMillis] = value } }
    suspend fun setAlarmSoundUri(value: String?) { edit { if (value == null) it.remove(Keys.alarmSoundUri) else it[Keys.alarmSoundUri] = value } }
    suspend fun bumpRingChannelVersion(): Int { var next = 1; context.settingsDataStore.edit { next = (it[Keys.ringChannelVersion]?.takeIf { value -> value > 0 } ?: 1) + 1; it[Keys.ringChannelVersion] = next }; return next }
    suspend fun setRingFullScreenBlocked(value: Boolean) { edit { it[Keys.ringFullScreenBlocked] = value } }
    suspend fun setOnboardingCompleted(value: Boolean) { edit { it[Keys.onboardingCompleted] = value } }
    suspend fun setDemoMode(value: Boolean) { edit { it[Keys.demoMode] = value } }
    suspend fun setLastTestScheduledMillis(value: Long?) { edit { if (value == null) it.remove(Keys.lastTestScheduledMillis) else it[Keys.lastTestScheduledMillis] = value } }
    suspend fun setLastNotifiedReleaseTag(value: String?) { edit { if (value == null) it.remove(Keys.lastNotifiedReleaseTag) else it[Keys.lastNotifiedReleaseTag] = value } }

    suspend fun clearApiKey() { edit { preferences -> preferences.remove(Keys.geminiApiKey); preferences[Keys.apiKeyStatus] = "NOT_SET"; preferences.remove(Keys.apiKeyReason); preferences[Keys.apiKeyFormat] = "UNKNOWN"; preferences.remove(Keys.apiKeyModel); preferences.remove(Keys.apiKeyLastVerifiedMillis) } }
    suspend fun clearUserDataExceptKey() { edit { preferences -> preferences.remove(Keys.disclaimerAccepted); preferences.remove(Keys.lastTestFiredMillis); preferences.remove(Keys.alarmSoundUri); preferences.remove(Keys.ringChannelVersion); preferences.remove(Keys.ringFullScreenBlocked) } }
    suspend fun resetForFullClear() { context.settingsDataStore.edit { it.clear() } }

    private suspend fun edit(block: suspend (androidx.datastore.preferences.core.MutablePreferences) -> Unit) { context.settingsDataStore.edit(block) }
    private fun requireInRange(value: Int, min: Int, max: Int, name: String) { require(value in min..max) { "$name must be between $min and $max" } }

    companion object {
        const val MAX_API_KEY_LENGTH = 256
        val API_KEY_STATUSES = setOf("NOT_SET", "OK_READY", "QUOTA_LIMITED", "FAILED")
        val API_KEY_FORMATS = setOf("LEGACY_AIZA", "NEW_AQ", "UNKNOWN")
    }
}
