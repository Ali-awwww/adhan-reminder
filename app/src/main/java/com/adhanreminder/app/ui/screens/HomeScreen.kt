package com.adhanreminder.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.adhanreminder.app.AdhanApplication
import com.adhanreminder.app.data.local.MonthSchedule
import com.adhanreminder.app.data.local.PrayerDay
import com.adhanreminder.app.data.local.ScheduleStatus
import com.adhanreminder.app.data.local.ScheduledAlarm
import com.adhanreminder.app.scheduling.NotificationChannels
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import java.util.concurrent.TimeUnit

@Composable
fun HomeScreen(onCapture: () -> Unit, onOpenDraft: (Long) -> Unit, onDeviceCheck: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as AdhanApplication
    val scope = rememberCoroutineScope()
    val schedules by app.repository.observeSchedules().collectAsState(initial = emptyList())
    val demoMode by app.settings.demoMode.collectAsState(initial = false)
    val approved = schedules.firstOrNull { it.status == ScheduleStatus.APPROVED }
    val draft = schedules.firstOrNull { it.status == ScheduleStatus.DRAFT }
    val notificationsOk = NotificationChannels.canPost(context)
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while (true) { now = System.currentTimeMillis(); delay(1_000) } }
    val alarms = approved?.let { app.repository.observeAlarms(it.id).collectAsState(initial = emptyList()).value } ?: emptyList()
    val nextAlarm = alarms.filter { !it.fired && it.triggerAtMillis >= now }.minByOrNull { it.triggerAtMillis }
    val days = approved?.let { app.repository.observePrayerDays(it.id).collectAsState(initial = emptyList()).value } ?: emptyList()
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("الرئيسية", style = MaterialTheme.typography.titleLarge)
        when {
            !notificationsOk && approved != null -> ProblemCard("الإشعارات مغلقة", onDeviceCheck)
            approved != null -> {
                NextPrayerCard(nextAlarm, now)
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("الجدول جاهز", style = MaterialTheme.typography.titleMedium)
                        Text("${alarms.count { !it.fired }} منبهات متبقية · ${approved.hijriMonthLabel}")
                        scheduleCoverage(days)?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    }
                }
            }
            draft != null -> Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text(if (demoMode) "جدول تجريبي بانتظار المراجعة" else "الجدول بانتظار المراجعة"); Button(onClick = { onOpenDraft(draft.id) }, modifier = Modifier.testTag("home_open_draft_button")) { Text("فتح المراجعة") } } }
            demoMode -> Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("الوضع التجريبي مفعّل"); Text("يمكنك إنشاء جدول محلي لتجربة الواجهات دون اتصال بالشبكة.", color = MaterialTheme.colorScheme.onSurfaceVariant); Button(onClick = { scope.launch { createDemoSchedule(app) } }, modifier = Modifier.testTag("home_create_demo_button")) { Text("إنشاء جدول تجريبي") } } }
            else -> Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("لم يُصوَّر جدول بعد"); Text("صوّر الجدول الشهري مرة واحدة، ثم راجعه قبل الاعتماد.", color = MaterialTheme.colorScheme.onSurfaceVariant); Button(onClick = onCapture, modifier = Modifier.testTag("home_empty_capture_button")) { Text("تصوير الجدول") } } }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) { Button(onClick = onCapture, modifier = Modifier.weight(1f).testTag("home_capture_button")) { Text("تصوير جدول") }; OutlinedButton(onClick = onDeviceCheck, modifier = Modifier.weight(1f).testTag("home_device_check_button")) { Text("فحص الجهاز") } }
    }
}

@Composable
private fun NextPrayerCard(alarm: ScheduledAlarm?, now: Long) {
    Card(modifier = Modifier.fillMaxWidth().semantics { contentDescription = "أقرب أذان" }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("أقرب تنبيه", color = MaterialTheme.colorScheme.onPrimary)
            Text(alarm?.prayerKey?.arabicName ?: "لا توجد منبهات قادمة", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimary)
            if (alarm != null) { Text(formatCountdown(alarm.triggerAtMillis - now), style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onPrimary); Text("${alarm.gregorianDate} · ${alarm.triggerAtMillis.formatTime()}", color = MaterialTheme.colorScheme.onPrimary) }
        }
    }
}

private fun ScheduledAlarm?.letName() = this?.prayerKey?.arabicName
private val com.adhanreminder.app.data.local.PrayerKey.arabicName: String get() = when (this) { com.adhanreminder.app.data.local.PrayerKey.FAJR -> "الفجر"; com.adhanreminder.app.data.local.PrayerKey.DHUHR -> "الظهر"; com.adhanreminder.app.data.local.PrayerKey.ASR -> "العصر"; com.adhanreminder.app.data.local.PrayerKey.MAGHRIB -> "المغرب"; com.adhanreminder.app.data.local.PrayerKey.ISHA -> "العشاء" }
private fun Long.formatTime(): String = java.time.Instant.ofEpochMilli(this).atZone(java.time.ZoneId.systemDefault()).toLocalTime().toString().take(5)
private fun formatCountdown(millis: Long): String { val safe = millis.coerceAtLeast(0); val h = TimeUnit.MILLISECONDS.toHours(safe); val m = TimeUnit.MILLISECONDS.toMinutes(safe) % 60; val s = TimeUnit.MILLISECONDS.toSeconds(safe) % 60; return "%02d:%02d:%02d".format(h, m, s) }
private fun scheduleCoverage(days: List<PrayerDay>): String? { val last = days.mapNotNull { runCatching { LocalDate.parse(it.gregorianDate) }.getOrNull() }.maxOrNull() ?: return null; return "التغطية حتى ${last} · متبقٍ ${ChronoUnit.DAYS.between(LocalDate.now(), last).coerceAtLeast(0)} يوم" }
private suspend fun createDemoSchedule(app: AdhanApplication) { val id = app.repository.insertSchedule(MonthSchedule(hijriMonthLabel = "تجريبي — بدون شبكة", createdAtMillis = System.currentTimeMillis(), status = ScheduleStatus.DRAFT)); val start = LocalDate.now(); app.repository.insertPrayerDays((0 until 7).map { n -> PrayerDay(scheduleId = id, gregorianDate = start.plusDays(n.toLong()).toString(), hijriDay = n + 1, fajr = "04:30", dhuhr = "12:00", asr = "15:30", maghrib = "18:45", isha = "20:15") }) }

@Composable
private fun ProblemCard(message: String, onOpen: () -> Unit) { Card(modifier = Modifier.semantics { contentDescription = "تحذير: $message" }, colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) { Text("⚠ $message", color = MaterialTheme.colorScheme.onPrimary); Button(onClick = onOpen) { Text("فحص الجهاز") } } } }
