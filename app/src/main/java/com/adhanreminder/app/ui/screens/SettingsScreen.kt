package com.adhanreminder.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.adhanreminder.app.AdhanApplication
import com.adhanreminder.app.BuildConfig
import com.adhanreminder.app.data.ScheduleBackupManager
import com.adhanreminder.app.data.local.MonthSchedule
import com.adhanreminder.app.data.local.PrayerDay
import com.adhanreminder.app.data.local.ScheduleStatus
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(onOpenDeviceCheck: () -> Unit, onClearData: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as AdhanApplication
    val settings = app.settings
    val scope = rememberCoroutineScope()
    val reminder by settings.reminderOffsetMinutes.collectAsState(initial = 10)
    val gap by settings.fajrGapMinutes.collectAsState(initial = 20)
    val rescan by settings.rescanReminderDays.collectAsState(initial = 3)
    val vibrate by settings.alarmVibrate.collectAsState(initial = true)
    val dark by settings.darkTheme.collectAsState(initial = true)
    val sound by settings.alarmSoundUri.collectAsState(initial = null)
    var tabIndex by remember { mutableIntStateOf(0) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    val demoMode by settings.demoMode.collectAsState(initial = false)

    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= 33) {
                result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java)
            } else {
                @Suppress("DEPRECATION")
                result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            }
            scope.launch { settings.setAlarmSoundUri(uri?.toString()) }
        }
    }

    val exportPicker = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val schedule = app.repository.getLatestApprovedSchedule() ?: error("لا يوجد جدول معتمد")
                val days = app.repository.getPrayerDays(schedule.id)
                val output = context.contentResolver.openOutputStream(uri) ?: error("تعذر فتح الملف")
                ScheduleBackupManager(context).export(schedule, days, output)
            }.onSuccess { backupMessage = "تم تصدير نسخة مشفّرة من الجدول" }
                .onFailure { backupMessage = it.message ?: "تعذر التصدير" }
        }
    }

    val importPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            runCatching {
                val input = context.contentResolver.openInputStream(uri) ?: error("تعذر فتح الملف")
                val imported = ScheduleBackupManager(context).import(input)
                val id = app.repository.insertSchedule(
                    MonthSchedule(
                        hijriMonthLabel = imported.hijriMonthLabel,
                        createdAtMillis = imported.createdAtMillis,
                        status = ScheduleStatus.DRAFT,
                    )
                )
                app.repository.insertPrayerDays(
                    imported.days.map { day ->
                        PrayerDay(
                            scheduleId = id,
                            gregorianDate = day.gregorianDate,
                            hijriDay = day.hijriDay,
                            fajr = day.fajr,
                            dhuhr = day.dhuhr,
                            asr = day.asr,
                            maghrib = day.maghrib,
                            isha = day.isha,
                            reviewFlagsJson = day.reviewFlagsJson,
                        )
                    }
                )
            }.onSuccess { backupMessage = "تم استيراد النسخة كمسودة للمراجعة" }
                .onFailure { backupMessage = it.message ?: "تعذر الاستيراد" }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("الإعدادات", style = MaterialTheme.typography.titleLarge)
        TabRow(selectedTabIndex = tabIndex) {
            listOf("التنبيهات", "المظهر", "الجهاز والبيانات").forEachIndexed { index, label ->
                Tab(
                    selected = tabIndex == index,
                    onClick = { tabIndex = index },
                    text = { Text(label) },
                    modifier = Modifier.testTag("settings_tab_$index"),
                )
            }
        }
        when (tabIndex) {
            0 -> {
                SettingStepper("التنبيه قبل الأذان", reminder, 1..60) {
                    scope.launch { settings.setReminderOffsetMinutes(it) }
                }
                SettingStepper("الفاصل قبل الفجر المكتوب", gap, 0..60) {
                    scope.launch { settings.setFajrGapMinutes(it) }
                }
                Text(
                    "الفجر: التنبيه قبل الأذان الأول بـ${gap + reminder} دقيقة",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                HorizontalDivider()
                SettingStepper("التذكير بتصوير جدول جديد قبل النهاية", rescan, 1..7) {
                    scope.launch { settings.setRescanReminderDays(it) }
                }
                SettingSwitch("اهتزاز المنبه", vibrate, "settings_vibrate_switch") {
                    scope.launch { settings.setAlarmVibrate(it) }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("صوت المنبه")
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        OutlinedButton(
                            onClick = {
                                soundPicker.launch(
                                    Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                        .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                        .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, sound?.let(Uri::parse))
                                )
                            },
                            modifier = Modifier.testTag("settings_pick_sound_button"),
                        ) { Text("اختيار") }
                        TextButton(
                            onClick = { sound?.let { RingtoneManager.getRingtone(context, Uri.parse(it))?.play() } },
                            modifier = Modifier.testTag("settings_test_sound_button"),
                        ) { Text("اختبار") }
                    }
                }
            }
            1 -> {
                SettingSwitch("الوضع الداكن", dark, "settings_dark_theme_switch") {
                    scope.launch { settings.setDarkTheme(it) }
                }
                HorizontalDivider()
                SettingSwitch("الوضع التجريبي بدون شبكة", demoMode, "settings_demo_mode_switch") {
                    scope.launch { settings.setDemoMode(it) }
                }
                Text(
                    "ينشئ جدولاً محلياً تجريبياً لتجربة المراجعة والتنقل دون إرسال صورة أو الاتصال بالخدمة.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            else -> {
                Button(
                    onClick = onOpenDeviceCheck,
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("settings_device_check_button"),
                ) { Text("فحص الجهاز") }
                OutlinedButton(
                    onClick = { exportPicker.launch("adhan-reminder-backup.bin") },
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("settings_export_button"),
                ) { Text("تصدير نسخة احتياطية مشفّرة") }
                OutlinedButton(
                    onClick = { importPicker.launch(arrayOf("application/octet-stream", "application/json")) },
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("settings_import_button"),
                ) { Text("استيراد نسخة احتياطية") }
                backupMessage?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                OutlinedButton(
                    onClick = onClearData,
                    modifier = Modifier.fillMaxWidth().minimumInteractiveComponentSize().testTag("settings_clear_data_button"),
                ) { Text("مسح البيانات") }
            }
        }
        Text(
            "الإصدار ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SettingStepper(title: String, value: Int, range: IntRange, onValue: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(title)
            Text(value.toString())
        }
        Slider(
            value = value.toFloat(),
            onValueChange = { onValue(it.toInt().coerceIn(range)) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
        )
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, testTag: String, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title)
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            modifier = Modifier.testTag(testTag),
        )
    }
}
