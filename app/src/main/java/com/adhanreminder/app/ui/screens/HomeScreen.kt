package com.adhanreminder.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.adhanreminder.app.AdhanApplication
import com.adhanreminder.app.data.local.ScheduleStatus
import com.adhanreminder.app.scheduling.NotificationChannels
import java.time.LocalDate
import java.time.temporal.ChronoUnit

@Composable
fun HomeScreen(onCapture: () -> Unit, onOpenDraft: (Long) -> Unit, onDeviceCheck: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as AdhanApplication
    val schedules by app.repository.observeSchedules().collectAsState(initial = emptyList())
    val approved = schedules.firstOrNull { it.status == ScheduleStatus.APPROVED }
    val draft = schedules.firstOrNull { it.status == ScheduleStatus.DRAFT }
    val notificationsOk = NotificationChannels.canPost(context)
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("الرئيسية", style = MaterialTheme.typography.titleLarge)
        when {
            !notificationsOk && approved != null -> ProblemCard("الإشعارات مغلقة", onDeviceCheck)
            approved != null -> {
                val alarms = app.repository.observeAlarms(approved.id).collectAsState(initial = emptyList()).value
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) { Column(Modifier.padding(16.dp)) { Text("الجدول جاهز · ${alarms.count { !it.fired }} منبهاً"); Text(approved.hijriMonthLabel) } }
            }
            draft != null -> Card { Column(Modifier.padding(16.dp)) { Text("الجدول بانتظار المراجعة"); Button(onClick = { onOpenDraft(draft.id) }) { Text("فتح المراجعة") } } }
            else -> Card { Column(Modifier.padding(16.dp)) { Text("لم يُصوَّر جدول بعد"); Button(onClick = onCapture) { Text("تصوير الجدول") } } }
        }
        approved?.let { schedule ->
            val days = app.repository.observePrayerDays(schedule.id).collectAsState(initial = emptyList()).value
            val last = days.mapNotNull { runCatching { LocalDate.parse(it.gregorianDate) }.getOrNull() }.maxOrNull()
            if (last != null) Text("متبقٍ ${ChronoUnit.DAYS.between(LocalDate.now(), last).coerceAtLeast(0)} يوم من الجدول")
        }
        Button(onClick = onCapture, modifier = Modifier.fillMaxWidth()) { Text("تصوير الجدول الجديد") }
        OutlinedButton(onClick = onDeviceCheck, modifier = Modifier.fillMaxWidth()) { Text("فحص الجهاز") }
    }
}

@Composable
private fun ProblemCard(message: String, onOpen: () -> Unit) {
    Card(modifier = Modifier.semantics { contentDescription = "تحذير: $message" }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) { Column(Modifier.padding(16.dp)) { Text("⚠ $message", color = MaterialTheme.colorScheme.onPrimary); Button(onClick = onOpen) { Text("فحص الجهاز") } } }
}
