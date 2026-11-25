package com.adhanreminder.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adhanreminder.app.AdhanApplication
import com.adhanreminder.app.R
import com.adhanreminder.app.data.local.PrayerDay
import com.adhanreminder.app.domain.PrayerTimes24
import com.adhanreminder.app.domain.ReviewFlag
import com.adhanreminder.app.domain.ScheduleConflictReport
import com.adhanreminder.app.domain.TimetableValidator
import com.adhanreminder.app.scheduling.SchedulingEngine
import com.adhanreminder.app.ui.theme.AdhanDimensions
import com.adhanreminder.app.ui.theme.PlexMono
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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
    val settingsOffset by app.settings.reminderOffsetMinutes.collectAsState(initial = 10)
    val scope = rememberCoroutineScope()

    var onlyFlagged by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<EditTarget?>(null) }
    var confirmApproval by remember { mutableStateOf(false) }
    var conflictReport by remember { mutableStateOf<ScheduleConflictReport?>(null) }
    var checkingOrSaving by remember { mutableStateOf(false) }
    var showStartDateDialog by remember { mutableStateOf(false) }

    val mandatoryFlagsCount = days.sumOf { day -> flagsOf(day).count { TimetableValidator.isMandatoryFlag(it) } }
    val advisoryFlagsCount = days.sumOf { day -> flagsOf(day).count { TimetableValidator.isAdvisoryFlag(it) } }
    val advisoryReasons = days.flatMap { flagsOf(it) }
        .filter { TimetableValidator.isAdvisoryFlag(it) }
        .map { it.reason }
        .distinct()

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AdhanDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("مراجعة الجدول", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = 52.dp).testTag("review_back_button"),
                shape = RoundedCornerShape(AdhanDimensions.fieldRadius),
            ) {
                Icon(painter = painterResource(R.drawable.ic_arrow_back_rtl), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("رجوع", style = MaterialTheme.typography.labelLarge)
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(AdhanDimensions.cardRadius),
            border = BorderStroke(AdhanDimensions.border, MaterialTheme.colorScheme.outline),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    schedule?.hijriMonthLabel ?: "جدول جديد",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "${days.size} يوماً · أخطاء خلايا للمراجعة: $mandatoryFlagsCount",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    "اضغط على أي وقت لتعديله قبل الاعتماد. المنبه يرن قبل كل أذان بـ $settingsOffset دقائق.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
        }

        if (advisoryFlagsCount > 0) {
            Card(
                modifier = Modifier.fillMaxWidth().testTag("review_advisory_card"),
                shape = RoundedCornerShape(AdhanDimensions.cardRadius),
                border = BorderStroke(AdhanDimensions.border, MaterialTheme.colorScheme.outline),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("review_dismiss_advisory_button"),
                            shape = RoundedCornerShape(AdhanDimensions.fieldRadius),
                        ) { Text("تأكيد المتابعة", style = MaterialTheme.typography.labelLarge) }
                        OutlinedButton(
                            onClick = { showStartDateDialog = true },
                            modifier = Modifier.weight(1f).heightIn(min = 52.dp).testTag("review_fix_start_date_button"),
                            shape = RoundedCornerShape(AdhanDimensions.fieldRadius),
                        ) { Text("ضبط تاريخ البداية", style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("عرض الأيام المعلَّمة فقط", style = MaterialTheme.typography.bodyLarge)
            Switch(
                checked = onlyFlagged,
                onCheckedChange = { onlyFlagged = it },
                modifier = Modifier.testTag("review_only_flagged_switch"),
            )
        }

        days.filter { !onlyFlagged || flagsOf(it).isNotEmpty() }.forEach { day ->
            DayCard(day, settingsOffset, editable = true) { field -> selected = EditTarget(day, field) }
        }
        if (days.isEmpty()) Text("لا توجد أيام للعرض.", style = MaterialTheme.typography.bodyLarge)

        Button(
            enabled = mandatoryFlagsCount == 0 && advisoryFlagsCount == 0 && days.isNotEmpty() && !checkingOrSaving,
            onClick = {
                scope.launch {
                    checkingOrSaving = true
                    val report = SchedulingEngine(context, app.repository, app.settings).inspectApprovalConflicts(scheduleId)
                    checkingOrSaving = false
                    if (report != null) {
                        conflictReport = report
                    } else {
                        confirmApproval = true
                    }
                }
            },
            modifier = Modifier.fillMaxWidth().heightIn(min = AdhanDimensions.buttonHeight).testTag("review_approve_button"),
            shape = RoundedCornerShape(AdhanDimensions.cardRadius),
        ) { Text(if (checkingOrSaving) "جارٍ التحقق…" else "اعتماد وجدولة الشهر", style = MaterialTheme.typography.titleMedium) }
    }

    if (confirmApproval) {
        AlertDialog(
            onDismissRequest = { if (!checkingOrSaving) confirmApproval = false },
            title = { Text("اعتماد الجدول؟", style = MaterialTheme.typography.titleMedium) },
            text = {
                Text(
                    "سيصبح هذا الجدول هو المصدر الوحيد للتنبيهات، وستُعاد جدولة المنبهات قبل كل أذان بـ $settingsOffset دقائق. يمكنك تعديل الأيام لاحقاً من شاشة الجدول.",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                Button(
                    enabled = !checkingOrSaving,
                    onClick = {
                        confirmApproval = false
                        scope.launch {
                            checkingOrSaving = true
                            SchedulingEngine(context, app.repository, app.settings).approveSchedule(scheduleId)
                            checkingOrSaving = false
                            withContext(Dispatchers.Main.immediate) { onApproved() }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("review_confirm_approve_button"),
                ) { Text("اعتماد وجدولة", style = MaterialTheme.typography.labelLarge) }
            },
            dismissButton = {
                OutlinedButton(
                    enabled = !checkingOrSaving,
                    onClick = { confirmApproval = false },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
                ) { Text("إلغاء", style = MaterialTheme.typography.labelLarge) }
            },
        )
    }

    conflictReport?.let { report ->
        ScheduleConflictDialog(
            report = report,
            busy = checkingOrSaving,
            onMerge = {
                scope.launch {
                    checkingOrSaving = true
                    SchedulingEngine(context, app.repository, app.settings)
                        .approveWithMerge(newScheduleId = scheduleId, existingScheduleId = report.existingScheduleId)
                    checkingOrSaving = false
                    conflictReport = null
                    withContext(Dispatchers.Main.immediate) { onApproved() }
                }
            },
            onKeepLatest = {
                scope.launch {
                    checkingOrSaving = true
                    SchedulingEngine(context, app.repository, app.settings).approveSchedule(scheduleId)
                    checkingOrSaving = false
                    conflictReport = null
                    withContext(Dispatchers.Main.immediate) { onApproved() }
                }
            },
            onDismiss = { if (!checkingOrSaving) conflictReport = null },
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
                            ),
                        )
                    }
                }
            },
        )
    }

    selected?.let { target ->
        TimeEditDialog(target, onDismiss = { selected = null }) { updated ->
            scope.launch { app.repository.updatePrayerDay(updated); selected = null }
        }
    }
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
        title = { Text("ضبط تاريخ البداية", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("أدخل تاريخ اليوم الأول بصيغة YYYY-MM-DD لإعادة ترقيم الأيام بالتسلسل:", style = MaterialTheme.typography.bodyLarge)
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
                modifier = Modifier.heightIn(min = 52.dp).testTag("review_start_date_apply_button"),
            ) { Text("تطبيق", style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("إلغاء", style = MaterialTheme.typography.labelLarge) } },
    )
}

@Composable
private fun ScheduleConflictDialog(
    report: ScheduleConflictReport,
    busy: Boolean,
    onMerge: () -> Unit,
    onKeepLatest: () -> Unit,
    onDismiss: () -> Unit,
) {
    val previewLimit = 4
    val extraCount = (report.conflicts.size - previewLimit).coerceAtLeast(0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعارض في أوقات الصلاة", style = MaterialTheme.typography.titleMedium) },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    "يختلف هذا الجدول مع الجدول المعتمد حالياً (${report.existingScheduleLabel}) في ${report.conflicts.size} وقتاً ضمن ${report.conflictingDateCount} يوماً مشتركاً:",
                    style = MaterialTheme.typography.bodySmall,
                )
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("review_conflict_list_card"),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                ) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        report.conflicts.take(previewLimit).forEach { conflict ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    "${conflict.gregorianDate} · ${conflict.prayerLabelAr}",
                                    style = MaterialTheme.typography.bodySmall,
                                )
                                Text(
                                    "${conflict.existingTime} ← ${conflict.newTime}",
                                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = PlexMono),
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        if (extraCount > 0) {
                            Text(
                                "و$extraCount فروقات أخرى في التواريخ المشتركة",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
                if (report.nonOverlappingOldDayCount > 0) {
                    Text(
                        "• الدمج: يحفظ ${report.nonOverlappingOldDayCount} يوماً غير متعارض من السابق مع اعتماد الأحدث للأيام المشتركة وأرشفة الجدول القديم.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        "• الدمج أو الاحتفاظ بالأحدث سيعتمد أوقات هذا الجدول ويؤرشف الجدول السابق.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        enabled = !busy,
                        onClick = onMerge,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("review_conflict_merge_button"),
                    ) { Text("دمج البيانات (الأحدث للمشترك)", style = MaterialTheme.typography.labelLarge) }
                    OutlinedButton(
                        enabled = !busy,
                        onClick = onKeepLatest,
                        modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp).testTag("review_conflict_keep_latest_button"),
                    ) { Text("الاحتفاظ بالجدول الأحدث", style = MaterialTheme.typography.labelLarge) }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(
                enabled = !busy,
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 52.dp).testTag("review_conflict_cancel_button"),
            ) { Text("إلغاء", style = MaterialTheme.typography.labelLarge) }
        },
    )
}

data class EditTarget(val day: PrayerDay, val field: String)

@Composable
fun DayCard(
    day: PrayerDay,
    reminderOffset: Int,
    editable: Boolean,
    isToday: Boolean = false,
    onCellClick: (String) -> Unit = {},
) {
    val flags = flagsOf(day)
    val mandatoryFlags = flags.filter { TimetableValidator.isMandatoryFlag(it) }
    val borderColor = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    val borderWidth = if (isToday) 2.5.dp else AdhanDimensions.border
    val prayerFields = listOf(
        "fajr" to "الفجر",
        "dhuhr" to "الظهر",
        "asr" to "العصر",
        "maghrib" to "المغرب",
        "isha" to "العشاء",
    )

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AdhanDimensions.cardRadius),
        border = BorderStroke(borderWidth, borderColor),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    day.gregorianDate + (day.hijriDay?.let { " · $it هـ" } ?: ""),
                    style = MaterialTheme.typography.titleSmall,
                )
                if (isToday) {
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.primary,
                            contentColor = MaterialTheme.colorScheme.onPrimary,
                        ),
                        shape = RoundedCornerShape(AdhanDimensions.pillRadius),
                    ) {
                        Text(
                            "اليوم",
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                        )
                    }
                }
            }
            mandatoryFlags.filter { it.field == "day" }.forEach { dayFlag ->
                Text("⚠ ${dayFlag.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }

            prayerFields.chunked(2).forEach { pair ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    pair.forEach { (field, label) ->
                        val time = day.valueFor(field)
                        val flag = mandatoryFlags.firstOrNull { it.field == field }
                        PrayerCellCard(
                            label = label,
                            time = time,
                            reminderTime = reminderFor(time, reminderOffset),
                            flag = flag,
                            editable = editable,
                            onClick = { onCellClick(field) },
                            modifier = Modifier.weight(1f).testTag("review_cell_${day.gregorianDate}_$field"),
                        )
                    }
                    if (pair.size == 1) {
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun PrayerCellCard(
    label: String,
    time: String,
    reminderTime: String,
    flag: ReviewFlag?,
    editable: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = { if (editable) onClick() },
        enabled = editable,
        modifier = modifier
            .heightIn(min = 78.dp)
            .semantics { contentDescription = if (flag == null) "$label $time" else "$label $time، ${flag.reason}" },
        border = BorderStroke(
            width = if (flag != null) 2.dp else 1.dp,
            color = if (flag != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.outline,
        ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurface,
        ),
        shape = RoundedCornerShape(AdhanDimensions.fieldRadius),
    ) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Bold)
            Text(
                time.ifBlank { "—" },
                style = MaterialTheme.typography.titleMedium.copy(fontFamily = PlexMono),
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "التنبيه $reminderTime",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (flag != null) {
                Text("⚠ ${flag.reason}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimeEditDialog(target: EditTarget, onDismiss: () -> Unit, onSave: (PrayerDay) -> Unit) {
    val old = target.day.valueFor(target.field).ifBlank { "00:00" }
    val parts = old.split(":")
    val state = rememberTimePickerState(
        initialHour = parts.getOrNull(0)?.toIntOrNull() ?: 0,
        initialMinute = parts.getOrNull(1)?.toIntOrNull() ?: 0,
        is24Hour = true,
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("تعديل وقت ${arabicFieldName(target.field)}", style = MaterialTheme.typography.titleMedium) },
        text = { TimePicker(state = state) },
        confirmButton = {
            Button(
                onClick = {
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
                },
                modifier = Modifier.heightIn(min = 52.dp).testTag("time_edit_save_button"),
            ) { Text("حفظ", style = MaterialTheme.typography.labelLarge) }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                modifier = Modifier.heightIn(min = 52.dp),
            ) { Text("إلغاء", style = MaterialTheme.typography.labelLarge) }
        },
    )
}

private fun arabicFieldName(field: String): String = when (field) {
    "fajr" -> "الفجر"
    "dhuhr" -> "الظهر"
    "asr" -> "العصر"
    "maghrib" -> "المغرب"
    "isha" -> "العشاء"
    else -> field
}

private fun PrayerDay.valueFor(field: String): String = when (field) { "fajr" -> fajr; "dhuhr" -> dhuhr; "asr" -> asr; "maghrib" -> maghrib; else -> isha }
private fun PrayerDay.withValue(field: String, value: String): PrayerDay = when (field) { "fajr" -> copy(fajr = value); "dhuhr" -> copy(dhuhr = value); "asr" -> copy(asr = value); "maghrib" -> copy(maghrib = value); else -> copy(isha = value) }
private fun reminderFor(time: String, offset: Int): String = if (time.isBlank()) "—" else subtract(time, offset)
private fun subtract(time: String, minutes: Int): String = runCatching { LocalTime.parse(time).minusMinutes(minutes.toLong()).format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT)) }.getOrDefault("—")
private fun flagsOf(day: PrayerDay): List<ReviewFlag> = runCatching { val a = JSONArray(day.reviewFlagsJson); (0 until a.length()).map { val o = a.getJSONObject(it); ReviewFlag(o.optString("gregorianDate"), o.optString("field"), o.optString("reason")) } }.getOrDefault(emptyList())
private fun flagsJson(flags: List<ReviewFlag>): String = JSONArray(flags.map { JSONObject().put("gregorianDate", it.gregorianDate).put("field", it.field).put("reason", it.reason) }).toString()
