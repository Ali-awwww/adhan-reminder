package com.adhanreminder.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adhanreminder.app.AdhanApplication
import com.adhanreminder.app.data.local.ScheduleStatus
import com.adhanreminder.app.scheduling.SchedulingEngine
import kotlinx.coroutines.launch

@Composable
fun ScheduleScreen() {
    val context = LocalContext.current
    val app = context.applicationContext as AdhanApplication
    val schedules by app.repository.observeSchedules().collectAsState(initial = emptyList())
    val schedule = schedules.firstOrNull { it.status == ScheduleStatus.APPROVED }
    val days = if (schedule == null) emptyList() else app.repository.observePrayerDays(schedule.id).collectAsState(initial = emptyList()).value
    val gap by app.settings.fajrGapMinutes.collectAsState(initial = 20)
    val offset by app.settings.reminderOffsetMinutes.collectAsState(initial = 10)
    val scope = rememberCoroutineScope()
    var editing by remember { mutableStateOf(false) }
    var selected by remember { mutableStateOf<EditTarget?>(null) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("الجدول", style = MaterialTheme.typography.titleLarge)
        if (schedule == null) {
            Text("لا يوجد جدول معتمد. صوّر الجدول من الرئيسية.")
        } else {
            Text(schedule.hijriMonthLabel)
            Text("متبقٍ ${days.size} يوماً")
            OutlinedButton(onClick = { editing = !editing }) { Text(if (editing) "إنهاء التعديل" else "تعديل") }
            days.forEach { day -> DayCard(day, gap, offset, editable = editing) { field -> selected = EditTarget(day, field) } }
        }
    }
    selected?.let { target -> TimeEditDialog(target, gap, onDismiss = { selected = null }) { updated ->
        scope.launch { app.repository.updatePrayerDay(updated); SchedulingEngine(context, app.repository, app.settings).rescheduleDay(updated.gregorianDate); selected = null }
    } }
}
