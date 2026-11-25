package com.adhanreminder.app.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
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
import com.adhanreminder.app.data.BuiltInTimetable
import com.adhanreminder.app.data.local.MonthSchedule
import com.adhanreminder.app.data.local.PrayerDay
import com.adhanreminder.app.data.local.PrayerKey
import com.adhanreminder.app.data.local.ScheduleStatus
import com.adhanreminder.app.data.local.ScheduledAlarm
import com.adhanreminder.app.scheduling.NotificationChannels
import com.adhanreminder.app.ui.theme.AdhanDimensions
import com.adhanreminder.app.ui.theme.PlexMono
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.concurrent.TimeUnit

@Composable
fun HomeScreen(
    onCapture: () -> Unit,
    onOpenSchedule: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenDraft: (Long) -> Unit,
    onDeviceCheck: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as AdhanApplication
    val scope = rememberCoroutineScope()
    val schedules by app.repository.observeSchedules().collectAsState(initial = emptyList())
    val reminderOffset by app.settings.reminderOffsetMinutes.collectAsState(initial = 10)
    val demoMode by app.settings.demoMode.collectAsState(initial = false)
    val approved = schedules.firstOrNull { it.status == ScheduleStatus.APPROVED }
    val draft = schedules.firstOrNull { it.status == ScheduleStatus.DRAFT }
    val notificationsOk = NotificationChannels.canPost(context)
    val exactAlarmsOk = com.adhanreminder.app.scheduling.SchedulingEngine.canScheduleExactAlarms(context)

    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var restoreMessage by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }

    val alarms = approved?.let { app.repository.observeAlarms(it.id).collectAsState(initial = emptyList()).value } ?: emptyList()
    val nextAlarm = alarms.filter { !it.fired && it.triggerAtMillis >= now }.minByOrNull { it.triggerAtMillis }
    val days = approved?.let { app.repository.observePrayerDays(it.id).collectAsState(initial = emptyList()).value } ?: emptyList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AdhanDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("مذكّر الأذان", style = MaterialTheme.typography.titleLarge)
            Text(
                "تنبيه قبل الأذان بـ $reminderOffset دقائق",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (!notificationsOk && approved != null) {
            ProblemCard("الإشعارات مغلقة — افتح فحص الجهاز لتفعيل التنبيهات", onDeviceCheck)
        } else if (!exactAlarmsOk && approved != null) {
            ProblemCard("إذن المنبه الدقيق غير مفعّل — افتح فحص الجهاز لتفعيله", onDeviceCheck)
        }

        when {
            approved != null -> {
                NextPrayerCard(
                    alarm = nextAlarm,
                    now = now,
                    reminderOffset = reminderOffset,
                )
                ActiveScheduleCard(
                    schedule = approved,
                    pendingAlarmsCount = alarms.count { !it.fired && it.triggerAtMillis >= now },
                    days = days,
                    restoring = restoring,
                    restoreMessage = restoreMessage,
                    onRestoreBuiltIn = {
                        if (!restoring) {
                            scope.launch {
                                restoring = true
                                val count = BuiltInTimetable.restoreAsApproved(context, app.repository, app.settings)
                                restoring = false
                                restoreMessage = "تمت استعادة الجدول المدمج وضبط $count منبهاً"
                            }
                        }
                    },
                )
            }
            draft != null -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AdhanDimensions.cardRadius),
                    border = BorderStroke(AdhanDimensions.border, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(
                            if (demoMode) "جدول تجريبي بانتظار المراجعة" else "يوجد جدول بانتظار المراجعة",
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Button(
                            onClick = { onOpenDraft(draft.id) },
                            modifier = Modifier.fillMaxWidth().heightIn(min = AdhanDimensions.buttonHeight).testTag("home_open_draft_button"),
                            shape = RoundedCornerShape(AdhanDimensions.fieldRadius),
                        ) { Text("فتح المراجعة", style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
            else -> {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(AdhanDimensions.cardRadius),
                    border = BorderStroke(AdhanDimensions.border, MaterialTheme.colorScheme.outline),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                ) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text("لا يوجد جدول مفعّل حالياً", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "يمكنك استعادة جدول السفيرة المدمج فوراً أو تصوير جدول جديد.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        OutlinedButton(
                            onClick = {
                                if (!restoring) {
                                    scope.launch {
                                        restoring = true
                                        val count = BuiltInTimetable.restoreAsApproved(context, app.repository, app.settings)
                                        restoring = false
                                        restoreMessage = "تمت استعادة الجدول المدمج وضبط $count منبهاً"
                                    }
                                }
                            },
                            enabled = !restoring,
                            modifier = Modifier.fillMaxWidth().heightIn(min = AdhanDimensions.buttonHeight).testTag("home_restore_builtin_button"),
                            shape = RoundedCornerShape(AdhanDimensions.fieldRadius),
                        ) {
                            Icon(painter = painterResource(R.drawable.ic_restore_table), contentDescription = null, modifier = Modifier.size(24.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                if (restoring) "جارٍ الاستعادة…" else "استعادة الجدول المدمج",
                                style = MaterialTheme.typography.labelLarge,
                            )
                        }
                        if (demoMode) {
                            OutlinedButton(
                                onClick = { scope.launch { createDemoSchedule(app) } },
                                modifier = Modifier.fillMaxWidth().heightIn(min = AdhanDimensions.buttonHeight).testTag("home_create_demo_button"),
                                shape = RoundedCornerShape(AdhanDimensions.fieldRadius),
                            ) { Text("إنشاء جدول تجريبي", style = MaterialTheme.typography.labelLarge) }
                        }
                    }
                }
            }
        }

        // 3 clear large primary navigation buttons for an elderly muezzin
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onCapture,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AdhanDimensions.largeButtonHeight)
                    .testTag("home_capture_button"),
                shape = RoundedCornerShape(AdhanDimensions.cardRadius),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_camera_scan),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                )
                Spacer(Modifier.width(12.dp))
                Text("صوّر الجدول", style = MaterialTheme.typography.titleMedium)
            }

            OutlinedButton(
                onClick = onOpenSchedule,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AdhanDimensions.largeButtonHeight)
                    .testTag("home_schedule_button"),
                shape = RoundedCornerShape(AdhanDimensions.cardRadius),
                border = BorderStroke(AdhanDimensions.border, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_calendar_schedule),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text("الجدول", style = MaterialTheme.typography.titleMedium)
            }

            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AdhanDimensions.largeButtonHeight)
                    .testTag("home_settings_button"),
                shape = RoundedCornerShape(AdhanDimensions.cardRadius),
                border = BorderStroke(AdhanDimensions.border, MaterialTheme.colorScheme.outline),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                contentPadding = PaddingValues(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings_gear),
                    contentDescription = null,
                    modifier = Modifier.size(28.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(12.dp))
                Text("الإعدادات", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun ActiveScheduleCard(
    schedule: MonthSchedule,
    pendingAlarmsCount: Int,
    days: List<PrayerDay>,
    restoring: Boolean,
    restoreMessage: String?,
    onRestoreBuiltIn: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("home_active_schedule_card"),
        shape = RoundedCornerShape(AdhanDimensions.cardRadius),
        border = BorderStroke(AdhanDimensions.border, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                schedule.hijriMonthLabel.ifBlank { BuiltInTimetable.LABEL },
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("home_schedule_label"),
            )
            Text(
                "$pendingAlarmsCount منبهات قادمة",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            scheduleCoverage(days)?.let {
                Text(
                    it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(
                onClick = onRestoreBuiltIn,
                enabled = !restoring,
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = AdhanDimensions.buttonHeight)
                    .testTag("home_restore_builtin_button"),
                shape = RoundedCornerShape(AdhanDimensions.fieldRadius),
                border = BorderStroke(AdhanDimensions.border, MaterialTheme.colorScheme.primary),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_restore_table),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (restoring) "جارٍ الاستعادة…" else "استعادة الجدول المدمج",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            restoreMessage?.let { msg ->
                Text(
                    msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.testTag("home_restore_message"),
                )
            }
        }
    }
}

@Composable
private fun NextPrayerCard(alarm: ScheduledAlarm?, now: Long, reminderOffset: Int) {
    Card(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "أقرب أذان" },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
        ),
    ) {
        Column(Modifier.padding(22.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "أقرب تنبيه (قبل الأذان بـ $reminderOffset دقائق)",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            Text(
                alarm?.prayerKey?.arabicName ?: "لا توجد منبهات قادمة",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onPrimary,
            )
            if (alarm != null) {
                Text(
                    formatCountdown(alarm.triggerAtMillis - now),
                    style = MaterialTheme.typography.displayLarge.copy(fontFamily = PlexMono, fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                val triggerTime = alarm.triggerAtMillis.formatTime()
                val adhanTime = Instant.ofEpochMilli(alarm.triggerAtMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalTime()
                    .plusMinutes(reminderOffset.toLong())
                    .format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT))
                Text(
                    "المنبه: $triggerTime · الأذان: $adhanTime",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
                Text(
                    alarm.gregorianDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}

private val PrayerKey.arabicName: String
    get() = when (this) {
        PrayerKey.FAJR -> "الفجر"
        PrayerKey.DHUHR -> "الظهر"
        PrayerKey.ASR -> "العصر"
        PrayerKey.MAGHRIB -> "المغرب"
        PrayerKey.ISHA -> "العشاء"
    }

private fun Long.formatTime(): String =
    Instant.ofEpochMilli(this).atZone(ZoneId.systemDefault()).toLocalTime()
        .format(DateTimeFormatter.ofPattern("HH:mm", Locale.ROOT))

private fun formatCountdown(millis: Long): String {
    val safe = millis.coerceAtLeast(0)
    val h = TimeUnit.MILLISECONDS.toHours(safe)
    val m = TimeUnit.MILLISECONDS.toMinutes(safe) % 60
    val s = TimeUnit.MILLISECONDS.toSeconds(safe) % 60
    return "%02d:%02d:%02d".format(Locale.ROOT, h, m, s)
}

private fun scheduleCoverage(days: List<PrayerDay>): String? {
    val last = days.mapNotNull { runCatching { LocalDate.parse(it.gregorianDate) }.getOrNull() }.maxOrNull() ?: return null
    val remaining = ChronoUnit.DAYS.between(LocalDate.now(), last).coerceAtLeast(0)
    return "التغطية حتى $last · متبقٍ $remaining يوم"
}

private suspend fun createDemoSchedule(app: AdhanApplication) {
    val id = app.repository.insertSchedule(
        MonthSchedule(
            hijriMonthLabel = "تجريبي — بدون شبكة",
            createdAtMillis = System.currentTimeMillis(),
            status = ScheduleStatus.DRAFT,
        ),
    )
    val start = LocalDate.now()
    app.repository.insertPrayerDays(
        (0 until 7).map { n ->
            PrayerDay(
                scheduleId = id,
                gregorianDate = start.plusDays(n.toLong()).toString(),
                hijriDay = n + 1,
                fajr = "04:30",
                dhuhr = "12:00",
                asr = "15:30",
                maghrib = "18:45",
                isha = "20:15",
            )
        },
    )
}

@Composable
private fun ProblemCard(message: String, onOpen: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().semantics { contentDescription = "تحذير: $message" },
        shape = RoundedCornerShape(AdhanDimensions.cardRadius),
        border = BorderStroke(2.dp, MaterialTheme.colorScheme.error),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "⚠ $message",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Button(
                onClick = onOpen,
                modifier = Modifier.fillMaxWidth().heightIn(min = AdhanDimensions.buttonHeight),
                shape = RoundedCornerShape(AdhanDimensions.fieldRadius),
            ) { Text("فحص الجهاز", style = MaterialTheme.typography.labelLarge) }
        }
    }
}
