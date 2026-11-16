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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adhanreminder.app.AdhanApplication
import com.adhanreminder.app.data.local.PrayerDay
import com.adhanreminder.app.domain.PrayerTimes24
import com.adhanreminder.app.domain.ReviewFlag
import com.adhanreminder.app.domain.TimetableValidator
import com.adhanreminder.app.scheduling.SchedulingEngine
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

@Composable
fun ReviewScreen(scheduleId: Long, onBack: () -> Unit, onApproved: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as AdhanApplication
    val schedule by app.repository.observeSchedule(scheduleId).collectAsState(initial = null)
    val days by app.repository.observePrayerDays(scheduleId).collectAsState(initial = emptyList())
    val settingsGap by app.settings.fajrGapMinutes.collectAsState(initial = 20)
    val settingsOffset by app.settings.reminderOffsetMinutes.collectAsState(initial = 10)
    val pendingFajrNote by app.settings.pendingFajrNote.collectAsState(initial = null)
    val scope = rememberCoroutineScope()

    var onlyFlagged by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<EditTarget?>(null) }
    var confirmApproval by remember { mutableStateOf(false) }
    var showStartDateDialog by remember { mutableStateOf(false) }

    val mandatoryFlagsCount = days.sumOf { day -> flagsOf(day).count { TimetableValidator.isMandatoryFlag(it) } }
    val advisoryFlagsCount = days.sumOf { day -> flagsOf(day).count { TimetableValidator.isAdvisoryFlag(it) } }
    val advisoryReasons = days.flatMap { flagsOf(it) }
        .filter { TimetableValidator.isAdvisoryFlag(it) }
        .map { it.reason }
        .distinct()

    val suggestedGap = pendingFajrNote?.takeIf { it.first == scheduleId && it.second != settingsGap }?.second

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("المراجعة", style = MaterialTheme.typography.titleLarge)
        Text("${days.size} يوماً · أخطاء خلايا للمراجعة: $mandatoryFlagsCount")

        if (suggestedGap != null) {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("review_fajr_suggestion_card"),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "ملاحظة الفجر من الجدول",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        "قرأ النموذج أن الأذان الأول قبل الفجر بـ $suggestedGap دقيقة (الإعداد الحالي: $settingsGap دقيقة).",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    app.settings.setFajrGapMinutes(suggestedGap)
                                    app.settings.clearPendingFajrNote()
                                }
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("review_apply_fajr_note_button"),
                        ) { Text("تطبيق $suggestedGap دقيقة") }
                        OutlinedButton(
                            onClick = { scope.launch { app.settings.clearPendingFajrNote() } },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("review_dismiss_fajr_note_button"),
                        ) { Text("إبقاء الحالي") }
                    }
                }
            }
        }

        if (advisoryFlagsCount > 0) {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("review_advisory_card"),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("تنبيه إرشادي للجدول", style = MaterialTheme.typography.titleSmall)
                    advisoryReasons.forEach { reason ->
                        Text("• $reason", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    days.forEach { day ->
                                        val remaining = flagsOf(day).filterNot { TimetableValidator.isAdvisoryFlag(it) }
                                        if (remaining.size != flagsOf(day).size) {
                                            app.repository.updatePrayerDay(day.copy(reviewFlagsJson = flagsJson(remaining)))
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("review_dismiss_advisory_button"),
                        ) { Text("تأكيد المتابعة") }
                        OutlinedButton(
                            onClick = { showStartDateDialog = true },
                            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("review_fix_start_date_button"),
                        ) { Text("ضبط تاريخ البداية") }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("المعلَّم فقط")
            Switch(
                checked = onlyFlagged,
                onCheckedChange = { onlyFlagged = it },
                modifier = Modifier.testTag("review_only_flagged_switch"),
            )
        }

        days.filter { !onlyFlagged || flagsOf(it).isNotEmpty() }.forEach { day ->
            DayCard(day, settingsGap, settingsOffset, editable = true) { field -> selected = EditTarget(day, field) }
        }
        if (days.isEmpty()) Text("لا توجد أيام للعرض.")

        Button(
            enabled = mandatoryFlagsCount == 0 && advisoryFlagsCount == 0 && days.isNotEmpty(),
            onClick = { confirmApproval = true },
            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("review_approve_button"),
        ) { Text("اعتماد وجدولة الشهر") }
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.heightIn(min = 48.dp).testTag("review_back_button"),
        ) { Text("رجوع") }
    }

    if (confirmApproval) {
        AlertDialog(
            onDismissRequest = { confirmApproval = false },
            title = { Text("اعتماد الجدول؟") },
            text = { Text("سيصبح هذا الجدول هو المصدر الوحيد للتنبيهات، وستُعاد جدولة المنبهات. يمكنك تعديل الأيام لاحقاً من تبويب الجدول.") },
            confirmButton = {
                Button(
                    onClick = {
                        confirmApproval = false
                        scope.launch {
                            SchedulingEngine(context, app.repository, app.settings).approveSchedule(scheduleId)
                            app.settings.clearPendingFajrNote()
                            onApproved()
                        }
                    },
                    modifier = Modifier.testTag("review_confirm_approve_button"),
                ) { Text("اعتماد وجدولة") }
            },
            dismissButton = {
                TextButton(onClick = { confirmApproval = false }) { Text("إلغاء") }
            },
        )
    }

    if (showStartDateDialog) {
        val initialStart = days.firstOrNull()?.gregorianDate
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?.toString()
            ?: LocalDate.now().toString()
        StartDateDialog(
            initialDate = initialStart,
            onDismiss = { showStartDateDialog = false },
            onApply = { startDate ->
                showStartDateDialog = false
                scope.launch {
                    days.forEachIndexed { index, day ->
                        val newDate = startDate.plusDays(index.toLong()).toString()
                        val remainingFlags = flagsOf(day)
                            .filterNot { it.field == "date" }
                            .map { it.copy(gregorianDate = newDate) }
                        app.repository.updatePrayerDay(
                            day.copy(
                                gregorianDate = newDate,
                                reviewFlagsJson = flagsJson(remainingFlags),
                            )
                        )
                    }
                }
            },
        )
    }

    selected?.let { target ->
        TimeEditDialog(target, settingsGap, onDismiss = { selected = null }) { updated ->
            scope.launch { app.repository.updatePrayerDay(updated); selected = null }
        }
    }
    @Suppress("UNUSED_VARIABLE") val unusedSchedule = schedule
}

@Composable
private fun StartDateDialog(
    initialDate: String,
    onDismiss: () -> Unit,
    onApply: (LocalDate) -> Unit,
) {
    var input by remember { mutableStateOf(initialDate) }
    val parsed = runCatching { LocalDate.parse(input.trim()) }.getOrNull()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("ضبط تاريخ البداية") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("أدخل تاريخ اليوم الأول بصيغة YYYY-MM-DD لإعادة ترقيم الأيام بالتسلسل:")
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    singleLine = true,
                    label = { Text("تاريخ البداية") },
                    isError = parsed == null,
                    modifier = Modifier.fillMaxWidth().testTag("review_start_date_input"),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { parsed?.let(onApply) },
                enabled = parsed != null,
                modifier = Modifier.testTag("review_start_date_apply_button"),
            ) { Text("تطبيق") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

data class EditTarget(val day: PrayerDay, val field: String)

@Composable
fun DayCard(day: PrayerDay, fajrGap: Int, reminderOffset: Int, editable: Boolean, onCellClick: (String) -> Unit = {}) {
    val flags = flagsOf(day)
    val mandatoryFlags = flags.filter { TimetableValidator.isMandatoryFlag(it) }
    Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(day.gregorianDate + (day.hijriDay?.let { " · هـ $it" } ?: ""))
            mandatoryFlags.filter { it.field == "day" }.forEach { dayFlag ->
                Text("⚠ ${dayFlag.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                listOf("fajr" to "الفجر", "dhuhr" to "الظهر", "asr" to "العصر", "maghrib" to "المغرب", "isha" to "العشاء").forEach { (field, label) ->
                    val time = day.valueFor(field)
                    val flag = mandatoryFlags.firstOrNull { it.field == field }
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
    val state = rememberTimePickerState(
        initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 0,
        initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل ${target.field}") },
        text = { TimePicker(state = state) },
        confirmButton = {
            TextButton(onClick = {
                val value = "%02d:%02d".format(Locale.ROOT, state.hour, state.minute)
                val updatedDay = target.day.withValue(target.field, value)
                val times = PrayerTimes24(
                    fajr = updatedDay.fajr.ifBlank { null },
                    dhuhr = updatedDay.dhuhr.ifBlank { null },
                    asr = updatedDay.asr.ifBlank { null },
                    maghrib = updatedDay.maghrib.ifBlank { null },
                    isha = updatedDay.isha.ifBlank { null },
                )
                val orderValid = TimetableValidator.hasValidPrayerOrder(times)
                val nextFlags = flagsOf(target.day)
                    .filterNot { it.field == target.field || (orderValid && it.field == "day") }
                    .toMutableList()
                if (!orderValid && nextFlags.none { it.field == "day" }) {
                    nextFlags.add(ReviewFlag(updatedDay.gregorianDate, "day", "ترتيب أوقات الصلاة داخل اليوم غير منطقي"))
                }
                onSave(updatedDay.copy(reviewFlagsJson = flagsJson(nextFlags)))
            }) { Text("حفظ") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء") } },
    )
}

private fun PrayerDay.valueFor(field: String): String = when (field) { "fajr" -> fajr; "dhuhr" -> dhuhr; "asr" -> asr; "maghrib" -> maghrib; else -> isha }
private fun PrayerDay.withValue(field: String, value: String): PrayerDay = when (field) { "fajr" -> copy(fajr = value); "dhuhr" -> copy(dhuhr = value); "asr" -> copy(asr = value); "maghrib" -> copy(maghrib = value); else -> copy(isha = value) }
private fun reminderFor(field: String, time: String, gap: Int, offset: Int): String = if (time.isBlank()) "—" else subtract(time, if (field == "fajr") gap + offset else offset)
private fun subtract(time: String, minutes: Int): String = runCatching { LocalTime.parse(time).minusMinutes(minutes.toLong()).format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)) }.getOrDefault("—")
private fun flagsOf(day: PrayerDay): List<ReviewFlag> = runCatching { val a = JSONArray(day.reviewFlagsJson); (0 until a.length()).map { val o = a.getJSONObject(it); ReviewFlag(o.optString("gregorianDate"), o.optString("field"), o.optString("reason")) } }.getOrDefault(emptyList())
private fun flagsJson(flags: List<ReviewFlag>): String = JSONArray(flags.map { JSONObject().put("gregorianDate", it.gregorianDate).put("field", it.field).put("reason", it.reason) }).toString()
