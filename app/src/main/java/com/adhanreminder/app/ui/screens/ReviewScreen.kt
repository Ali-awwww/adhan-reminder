package com.adhanreminder.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adhanreminder.app.AdhanApplication
import com.adhanreminder.app.data.local.MonthSchedule
import com.adhanreminder.app.data.local.PrayerDay
import com.adhanreminder.app.data.local.ScheduleStatus
import com.adhanreminder.app.scheduling.SchedulingEngine
import kotlinx.coroutines.launch
import org.json.JSONArray
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ReviewScreen(scheduleId: Long, onBack: () -> Unit, onApproved: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as AdhanApplication
    val schedule by app.repository.observeSchedule(scheduleId).collectAsState(initial = null)
    val days by app.repository.observePrayerDays(scheduleId).collectAsState(initial = emptyList())
    val settingsGap by app.settings.fajrGapMinutes.collectAsState(initial = 20)
    val settingsOffset by app.settings.reminderOffsetMinutes.collectAsState(initial = 10)
    val scope = rememberCoroutineScope()
    var onlyFlagged by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<EditTarget?>(null) }
    val flaggedCount = days.sumOf { flagsOf(it).size }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("المراجعة", style = MaterialTheme.typography.titleLarge)
        Text("${days.size} يوماً · يحتاج مراجعة: $flaggedCount")
        androidx.compose.material3.Switch(checked = onlyFlagged, onCheckedChange = { onlyFlagged = it })
        Text("المعلَّم فقط")
        days.filter { !onlyFlagged || flagsOf(it).isNotEmpty() }.forEach { day ->
            DayCard(day, settingsGap, settingsOffset, editable = true) { field -> selected = EditTarget(day, field) }
        }
        if (days.isEmpty()) Text("لا توجد أيام للعرض.")
        Button(enabled = flaggedCount == 0 && days.isNotEmpty(), onClick = {
            scope.launch {
                SchedulingEngine(context, app.repository, app.settings).approveSchedule(scheduleId)
                onApproved()
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("اعتماد وجدولة الشهر") }
        OutlinedButton(onClick = onBack) { Text("رجوع") }
    }
    selected?.let { target ->
        TimeEditDialog(target, settingsGap, onDismiss = { selected = null }) { updated ->
            scope.launch { app.repository.updatePrayerDay(updated); selected = null }
        }
    }
    @Suppress("UNUSED_VARIABLE") val unusedSchedule = schedule
}

data class EditTarget(val day: PrayerDay, val field: String)

@Composable
fun DayCard(day: PrayerDay, fajrGap: Int, reminderOffset: Int, editable: Boolean, onCellClick: (String) -> Unit = {}) {
    val flags = flagsOf(day)
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(day.gregorianDate + (day.hijriDay?.let { " · هـ $it" } ?: ""))
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("fajr" to "الفجر", "dhuhr" to "الظهر", "asr" to "العصر", "maghrib" to "المغرب", "isha" to "العشاء").forEach { (field, label) ->
                    val time = day.valueFor(field)
                    val flag = flags.firstOrNull { it.field == field }
                    Card(
                        onClick = { if (editable) onCellClick(field) },
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .semantics { contentDescription = if (flag == null) "$label $time" else "$label $time، ${flag.reason}" },
                        border = if (flag != null) BorderStroke(2.dp, MaterialTheme.colorScheme.onSurface) else null,
                        shape = RoundedCornerShape(8.dp),
                    ) {
                        Column(Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(label, style = MaterialTheme.typography.labelMedium)
                            Text(time.ifBlank { "—" }, style = MaterialTheme.typography.labelLarge)
                            Text("التنبيه ${reminderFor(field, time, fajrGap, reminderOffset)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (field == "fajr" && time.isNotBlank()) Text("الأذان الأول ${subtract(time, fajrGap)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            if (flag != null) Text("⚠ ${flag.reason}", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeEditDialog(target: EditTarget, fajrGap: Int, onDismiss: () -> Unit, onSave: (PrayerDay) -> Unit) {
    val old = target.day.valueFor(target.field).ifBlank { "00:00" }
    val parts = old.split(":")
    val state = rememberTimePickerState(initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 0, initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0, is24Hour = true)
    AlertDialog(onDismissRequest = onDismiss, title = { Text("تعديل ${target.field}") }, text = { TimePicker(state = state) }, confirmButton = { TextButton(onClick = {
        val value = "%02d:%02d".format(Locale.ROOT, state.hour, state.minute)
        val flags = flagsOf(target.day).filterNot { it.field == target.field }
        onSave(target.day.withValue(target.field, value).copy(reviewFlagsJson = flagsJson(flags)))
    }) { Text("حفظ") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } })
}

private fun PrayerDay.valueFor(field: String): String = when (field) { "fajr" -> fajr; "dhuhr" -> dhuhr; "asr" -> asr; "maghrib" -> maghrib; else -> isha }
private fun PrayerDay.withValue(field: String, value: String): PrayerDay = when (field) { "fajr" -> copy(fajr = value); "dhuhr" -> copy(dhuhr = value); "asr" -> copy(asr = value); "maghrib" -> copy(maghrib = value); else -> copy(isha = value) }
private fun reminderFor(field: String, time: String, gap: Int, offset: Int): String = if (time.isBlank()) "—" else subtract(time, if (field == "fajr") gap + offset else offset)
private fun subtract(time: String, minutes: Int): String = runCatching { LocalTime.parse(time).minusMinutes(minutes.toLong()).format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)) }.getOrDefault("—")
private fun flagsOf(day: PrayerDay): List<com.adhanreminder.app.domain.ReviewFlag> = runCatching { val a = JSONArray(day.reviewFlagsJson); (0 until a.length()).map { val o = a.getJSONObject(it); com.adhanreminder.app.domain.ReviewFlag(o.optString("gregorianDate"), o.optString("field"), o.optString("reason")) } }.getOrDefault(emptyList())
private fun flagsJson(flags: List<com.adhanreminder.app.domain.ReviewFlag>): String = JSONArray(flags.map { org.json.JSONObject().put("gregorianDate", it.gregorianDate).put("field", it.field).put("reason", it.reason) }).toString()
