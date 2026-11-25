package com.adhanreminder.app.ui.screens

import android.Manifest
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.adhanreminder.app.AdhanApplication
import com.adhanreminder.app.BuildConfig
import com.adhanreminder.app.R
import com.adhanreminder.app.device.ManufacturerMapping
import com.adhanreminder.app.scheduling.AlarmReceiver
import com.adhanreminder.app.scheduling.NotificationChannels
import com.adhanreminder.app.ui.theme.AdhanDimensions

@Composable
fun DeviceCheckScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as AdhanApplication
    var refreshToken by remember { mutableIntStateOf(0) }
    val ringVersion by app.settings.ringChannelVersion.collectAsState(initial = 1)
    @Suppress("UNUSED_EXPRESSION") refreshToken
    val notifications = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled() &&
        (Build.VERSION.SDK_INT < 33 || androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    val exactAlarms = com.adhanreminder.app.scheduling.SchedulingEngine.canScheduleExactAlarms(context)
    val battery = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    val fullScreen = Build.VERSION.SDK_INT < 34 || context.getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent()
    val ringChannel = context.getSystemService(android.app.NotificationManager::class.java).getNotificationChannel("adhan_ring_v$ringVersion")
    val lastTest by app.settings.lastTestFiredMillis.collectAsState(initial = null)
    val testPending = lastTest != null && lastTest!! > System.currentTimeMillis() - 10 * 60 * 1000L
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

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
            Text("فحص الجهاز", style = MaterialTheme.typography.titleLarge)
            OutlinedButton(
                onClick = onBack,
                modifier = Modifier.heightIn(min = 52.dp),
                shape = RoundedCornerShape(AdhanDimensions.fieldRadius),
            ) {
                Icon(painter = painterResource(R.drawable.ic_arrow_back_rtl), contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("رجوع", style = MaterialTheme.typography.labelLarge)
            }
        }

        Text(
            "تحقق من هذه الحالات لضمان رنين المنبه في وقته.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
            onClick = { refreshToken++ },
            modifier = Modifier.fillMaxWidth().heightIn(min = 54.dp),
            shape = RoundedCornerShape(AdhanDimensions.fieldRadius),
        ) { Text("إعادة الفحص", style = MaterialTheme.typography.labelLarge) }

        CheckRow("الإشعارات", notifications, if (!notifications) "السماح" else null) {
            if (Build.VERSION.SDK_INT >= 33) {
                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName))
            }
        }
        CheckRow("دقة المنبهات (المنبه الدقيق)", exactAlarms, if (!exactAlarms) "السماح بالمنبه الدقيق" else null) {
            context.startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}")))
        }
        CheckRow("قناة منبهات الأذان", ringChannel != null && ringChannel.importance >= android.app.NotificationManager.IMPORTANCE_DEFAULT, "إعدادات القناة") {
            context.startActivity(
                Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                    .putExtra(Settings.EXTRA_CHANNEL_ID, ringChannel?.id ?: NotificationChannels.ensureRing(context, ringVersion)),
            )
        }
        CheckRow("توفير البطارية", battery, if (!battery) "استثناء التطبيق" else null) {
            runCatching {
                context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}")))
            }.getOrElse {
                context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
        CheckRow("المنبهات بملء الشاشة", fullScreen, if (!fullScreen) "فتح الإعداد" else null) {
            if (Build.VERSION.SDK_INT >= 34) {
                context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}")))
            }
        }
        val guide = ManufacturerMapping.guideFor(Build.MANUFACTURER)
        CheckRow("التشغيل التلقائي (${guide.titleAr})", false, "فتح الدليل") {
            context.startActivity(Intent(context, ManufacturerGuideActivity::class.java))
        }
        Text(
            guide.stepsAr.joinToString(" "),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (BuildConfig.DEBUG) {
            CheckRow("اختبار المنبه", testPending, if (!testPending) "اختبار بعد دقيقتين" else null) {
                scheduleTest(context)
            }
        }
        if (lastTest != null && !testPending) {
            Text("لم يرنّ بعد، راجع خطوة التشغيل التلقائي.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
        }
        Text(
            "اترك التطبيق مفتوحاً أو مقفلاً في التطبيقات الأخيرة حتى لا يوقفه النظام.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().heightIn(min = AdhanDimensions.buttonHeight),
            shape = RoundedCornerShape(AdhanDimensions.fieldRadius),
        ) { Text("رجوع", style = MaterialTheme.typography.labelLarge) }
    }
}

@Composable
private fun CheckRow(title: String, ok: Boolean, action: String?, onAction: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(AdhanDimensions.cardRadius),
        border = BorderStroke(AdhanDimensions.border, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                "${if (ok) "✓" else "⚠"}  $title",
                style = MaterialTheme.typography.bodyLarge,
                color = if (ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            if (action != null) {
                Button(
                    onClick = onAction,
                    modifier = Modifier.fillMaxWidth().heightIn(min = 50.dp),
                    shape = RoundedCornerShape(AdhanDimensions.fieldRadius),
                ) { Text(action, style = MaterialTheme.typography.labelLarge) }
            }
        }
    }
}

private fun scheduleTest(context: android.content.Context) {
    val operation = PendingIntent.getBroadcast(context, 899999, Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_TEST), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val show = PendingIntent.getActivity(context, 899999, Intent(context, com.adhanreminder.app.MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val alarmManager = context.getSystemService(AlarmManager::class.java)
    com.adhanreminder.app.scheduling.SchedulingEngine.scheduleExactOrFallback(alarmManager, System.currentTimeMillis() + 120_000, show, operation)
}
