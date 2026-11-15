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
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adhanreminder.app.AdhanApplication
import com.adhanreminder.app.BuildConfig
import com.adhanreminder.app.scheduling.AlarmReceiver
import com.adhanreminder.app.device.ManufacturerMapping

@Composable
fun DeviceCheckScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as AdhanApplication
    val notifications = androidx.core.app.NotificationManagerCompat.from(context).areNotificationsEnabled() && (Build.VERSION.SDK_INT < 33 || androidx.core.content.ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED)
    val battery = context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
    val fullScreen = Build.VERSION.SDK_INT < 34 || context.getSystemService(android.app.NotificationManager::class.java).canUseFullScreenIntent()
    val lastTest by app.settings.lastTestFiredMillis.collectAsState(initial = null)
    val testPending = lastTest != null && lastTest!! > System.currentTimeMillis() - 10 * 60 * 1000L
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("فحص الجهاز", style = MaterialTheme.typography.titleLarge)
        CheckRow("الإشعارات", notifications, if (!notifications) "السماح" else null) { if (Build.VERSION.SDK_INT >= 33) permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else context.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)) }
        CheckRow("توفير البطارية", battery, if (!battery) "استثناء التطبيق" else null) { runCatching { context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))) }.getOrElse { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } }
        CheckRow("المنبهات بملء الشاشة", fullScreen, if (!fullScreen) "فتح الإعداد" else null) { if (Build.VERSION.SDK_INT >= 34) context.startActivity(Intent(Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:${context.packageName}"))) }
        val guide = ManufacturerMapping.guideFor(Build.MANUFACTURER)
        CheckRow("التشغيل التلقائي للشركة", false, "فتح شاشة الإعداد") { context.startActivity(Intent(context, ManufacturerGuideActivity::class.java)) }
        Text(guide.stepsAr.joinToString(" "), color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (BuildConfig.DEBUG) CheckRow("اختبار المنبه", testPending, if (!testPending) "اضبط منبه اختبار بعد دقيقتين" else null) { scheduleTest(context) }
        if (lastTest != null && !testPending) Text("لم يرنّ، راجع خطوة التشغيل التلقائي")
        Text("لا تُغلق التطبيق من إعدادات النظام. وقفله في الذاكرة الأخيرة يمنع النظام من إغلاقه.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onBack) { Text("رجوع") }
    }
}

@Composable
private fun CheckRow(title: String, ok: Boolean, action: String?, onAction: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("${if (ok) "✓" else "⚠"}  $title")
        if (action != null) Button(onClick = onAction) { Text(action) }
    }
}

private fun scheduleTest(context: android.content.Context) {
    val operation = PendingIntent.getBroadcast(context, 899999, Intent(context, AlarmReceiver::class.java).setAction(AlarmReceiver.ACTION_TEST), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    val show = PendingIntent.getActivity(context, 899999, Intent(context, com.adhanreminder.app.MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    context.getSystemService(AlarmManager::class.java).setAlarmClock(AlarmManager.AlarmClockInfo(System.currentTimeMillis() + 120_000, show), operation)
}
