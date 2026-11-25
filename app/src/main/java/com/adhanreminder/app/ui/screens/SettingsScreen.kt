package com.adhanreminder.app.ui.screens

import android.app.Activity
import android.content.Intent
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adhanreminder.app.AdhanApplication
import com.adhanreminder.app.BuildConfig
import com.adhanreminder.app.R
import com.adhanreminder.app.data.ScheduleBackupManager
import com.adhanreminder.app.data.local.MonthSchedule
import com.adhanreminder.app.data.local.PrayerDay
import com.adhanreminder.app.data.local.ScheduleStatus
import com.adhanreminder.app.scheduling.SchedulingEngine
import com.adhanreminder.app.ui.theme.AdhanDimensions
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenDeviceCheck: () -> Unit,
    onClearData: () -> Unit,
) {
    val context = LocalContext.current
    val app = context.applicationContext as AdhanApplication
    val settings = app.settings
    val scope = rememberCoroutineScope()
    val reminder by settings.reminderOffsetMinutes.collectAsState(initial = 10)
    val rescan by settings.rescanReminderDays.collectAsState(initial = 3)
    val vibrate by settings.alarmVibrate.collectAsState(initial = true)
    val dark by settings.darkTheme.collectAsState(initial = false)
    val sound by settings.alarmSoundUri.collectAsState(initial = null)
    val demoMode by settings.demoMode.collectAsState(initial = false)
    var backupMessage by remember { mutableStateOf<String?>(null) }
    var rescheduleMessage by remember { mutableStateOf<String?>(null) }

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
                    ),
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
                    },
                )
            }.onSuccess { backupMessage = "تم استيراد النسخة كمسودة للمراجعة" }
                .onFailure { backupMessage = it.message ?: "تعذر الاستيراد" }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(AdhanDimensions.screenPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("الإعدادات", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = 52.dp).testTag("settings_back_button"),
                shape = RoundedCornerShape(AdhanDimensions.fieldRadius),
            ) {
                Icon(painter = painterResource(R.drawable.ic_arrow_back_rtl), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("رجوع", style = MaterialTheme.typography.labelLarge)
            }
        }

        SettingsCard("التنبيهات والصوت") {
            LargeStepper(
                title = "التنبيه قبل كل أذان",
                valueText = "$reminder دقائق",
                canDecrease = reminder > 1,
                canIncrease = reminder < 60,
                onDecrease = {
                    val next = (reminder - 1).coerceIn(1, 60)
                    scope.launch {
                        settings.setReminderOffsetMinutes(next)
                        val count = SchedulingEngine(context, app.repository, settings).rescheduleAll()
                        rescheduleMessage = "تم ضبط التنبيهات قبل الأذان بـ $next دقائق ($count منبهاً)"
                    }
                },
                onIncrease = {
                    val next = (reminder + 1).coerceIn(1, 60)
                    scope.launch {
                        settings.setReminderOffsetMinutes(next)
                        val count = SchedulingEngine(context, app.repository, settings).rescheduleAll()
                        rescheduleMessage = "تم ضبط التنبيهات قبل الأذان بـ $next دقائق ($count منبهاً)"
                    }
                },
                testTagPrefix = "settings_reminder_offset",
            )
            Text(
                "يشمل جميع الصلوات الخمس (الفجر، الظهر، العصر، المغرب، العشاء) ولا يشمل الشروق.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            rescheduleMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider()
            LargeStepper(
                title = "التذكير بتصوير جدول جديد",
                valueText = "قبل $rescan أيام",
                canDecrease = rescan > 1,
                canIncrease = rescan < 7,
                onDecrease = { scope.launch { settings.setRescanReminderDays((rescan - 1).coerceIn(1, 7)) } },
                onIncrease = { scope.launch { settings.setRescanReminderDays((rescan + 1).coerceIn(1, 7)) } },
                testTagPrefix = "settings_rescan_days",
            )
            HorizontalDivider()
            SettingSwitch("اهتزاز المنبه", vibrate, "settings_vibrate_switch") {
                scope.launch { settings.setAlarmVibrate(it) }
            }
            HorizontalDivider()
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("صوت المنبه", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = {
                            soundPicker.launch(
                                Intent(RingtoneManager.ACTION_RINGTONE_PICKER)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM)
                                    .putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, sound?.let(Uri::parse)),
                            )
                        },
                        modifier = Modifier.weight(1f).heightIn(min = 54.dp).testTag("settings_pick_sound_button"),
                        shape = RoundedCornerShape(AdhanDimensions.fieldRadius),
                    ) { Text("اختيار الصوت", style = MaterialTheme.typography.labelLarge) }
                    OutlinedButton(
                        onClick = { sound?.let { RingtoneManager.getRingtone(context, Uri.parse(it))?.play() } },
                        modifier = Modifier.weight(1f).heightIn(min = 54.dp).testTag("settings_test_sound_button"),
                        shape = RoundedCornerShape(AdhanDimensions.fieldRadius),
                    ) { Text("تجربة الصوت", style = MaterialTheme.typography.labelLarge) }
                }
            }
        }

        SettingsCard("المظهر") {
            SettingSwitch("الوضع الداكن", dark, "settings_dark_theme_switch") {
                scope.launch { settings.setDarkTheme(it) }
            }
            Text(
                "الوضع الفاتح الهادئ موصى به لوضوح القراءة.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        SettingsCard("الجهاز والنسخ الاحتياطي") {
            Button(
                onClick = onOpenDeviceCheck,
                modifier = Modifier.fillMaxWidth().heightIn(min = AdhanDimensions.buttonHeight).testTag("settings_device_check_button"),
                shape = RoundedCornerShape(AdhanDimensions.fieldRadius),
            ) { Text("فحص الجهاز والإشعارات", style = MaterialTheme.typography.labelLarge) }
            OutlinedButton(
                onClick = { exportPicker.launch("adhan-reminder-backup.bin") },
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("settings_export_button"),
                shape = RoundedCornerShape(AdhanDimensions.fieldRadius),
            ) { Text("تصدير نسخة احتياطية", style = MaterialTheme.typography.labelLarge) }
            OutlinedButton(
                onClick = { importPicker.launch(arrayOf("application/octet-stream", "application/json")) },
                modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp).testTag("settings_import_button"),
                shape = RoundedCornerShape(AdhanDimensions.fieldRadius),
            ) { Text("استيراد نسخة احتياطية", style = MaterialTheme.typography.labelLarge) }
            backupMessage?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
            }
            HorizontalDivider()
            SettingSwitch("الوضع التجريبي بدون شبكة", demoMode, "settings_demo_mode_switch") {
                scope.launch { settings.setDemoMode(it) }
            }
            OutlinedButton(
                onClick = onClearData,
                modifier = Modifier.fillMaxWidth().heightIn(min = AdhanDimensions.buttonHeight).testTag("settings_clear_data_button"),
                shape = RoundedCornerShape(AdhanDimensions.fieldRadius),
                border = BorderStroke(AdhanDimensions.border, MaterialTheme.colorScheme.error),
            ) {
                Text("مسح البيانات", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.error)
            }
        }

        Text(
            "الإصدار ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().heightIn(min = AdhanDimensions.buttonHeight),
            shape = RoundedCornerShape(AdhanDimensions.fieldRadius),
        ) {
            Text("العودة للرئيسية", style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
private fun SettingsCard(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AdhanDimensions.cardRadius),
        border = BorderStroke(AdhanDimensions.border, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun LargeStepper(
    title: String,
    valueText: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    testTagPrefix: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedButton(
                onClick = onDecrease,
                enabled = canDecrease,
                modifier = Modifier.size(56.dp).testTag("${testTagPrefix}_minus"),
                shape = RoundedCornerShape(AdhanDimensions.fieldRadius),
            ) { Text("-", style = MaterialTheme.typography.titleMedium) }
            Text(valueText, style = MaterialTheme.typography.titleMedium)
            OutlinedButton(
                onClick = onIncrease,
                enabled = canIncrease,
                modifier = Modifier.size(56.dp).testTag("${testTagPrefix}_plus"),
                shape = RoundedCornerShape(AdhanDimensions.fieldRadius),
            ) { Text("+", style = MaterialTheme.typography.titleMedium) }
        }
    }
}

@Composable
private fun SettingSwitch(title: String, checked: Boolean, testTag: String, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().minimumInteractiveComponentSize(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f).padding(end = 12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onChecked,
            modifier = Modifier.testTag(testTag),
        )
    }
}
