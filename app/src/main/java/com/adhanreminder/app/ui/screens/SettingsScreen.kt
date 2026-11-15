package com.adhanreminder.app.ui.screens

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import com.adhanreminder.app.AdhanApplication
import com.adhanreminder.app.data.ApiKeyVerifier
import com.adhanreminder.app.domain.FailureReason
import com.adhanreminder.app.domain.KeyCheckResult
import com.adhanreminder.app.domain.KeyFormat
import com.adhanreminder.app.domain.maskKey
import com.adhanreminder.app.ui.components.KeyCard
import com.adhanreminder.app.ui.components.LowPriorityButton
import com.adhanreminder.app.ui.components.PrimaryButton
import com.adhanreminder.app.ui.components.ReasonCode
import com.adhanreminder.app.ui.components.SecondaryButton
import com.adhanreminder.app.ui.components.StatusRow
import com.adhanreminder.app.ui.theme.PlexMono
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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
    val storedKey by settings.geminiApiKey.collectAsState(initial = "")
    val keyStatus by settings.apiKeyStatus.collectAsState(initial = "NOT_SET")
    val keyReason by settings.apiKeyReason.collectAsState(initial = "")
    val keyFormat by settings.apiKeyFormat.collectAsState(initial = "UNKNOWN")
    val keyModel by settings.apiKeyModel.collectAsState(initial = "")
    val verifiedAt by settings.apiKeyLastVerifiedMillis.collectAsState(initial = null)
    val sound by settings.alarmSoundUri.collectAsState(initial = null)
    var key by remember(storedKey) { mutableStateOf(storedKey) }
    var editing by remember { mutableStateOf(storedKey.isBlank()) }
    var checking by remember { mutableStateOf(false) }
    var transientFailure by remember { mutableStateOf<KeyCheckResult.Failed?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    val soundPicker = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uri = if (Build.VERSION.SDK_INT >= 33) result.data?.getParcelableExtra(RingtoneManager.EXTRA_RINGTONE_PICKED_URI, Uri::class.java) else @Suppress("DEPRECATION") result.data?.getParcelableExtra<Uri>(RingtoneManager.EXTRA_RINGTONE_PICKED_URI)
            scope.launch { settings.setAlarmSoundUri(uri?.toString()) }
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Text("الإعدادات", style = MaterialTheme.typography.titleLarge)
        SettingStepper("التنبيه قبل الأذان", reminder, 1..60) { scope.launch { settings.setReminderOffsetMinutes(it) } }
        SettingStepper("الفاصل قبل الفجر المكتوب", gap, 0..60) { scope.launch { settings.setFajrGapMinutes(it) } }
        Text("الفجر: التنبيه قبل الأذان الأول بـ${gap + reminder} دقيقة", color = MaterialTheme.colorScheme.onSurfaceVariant)
        HorizontalDivider()
        SettingStepper("التذكير بتصوير جدول جديد قبل النهاية", rescan, 1..7) { scope.launch { settings.setRescanReminderDays(it) } }
        HorizontalDivider()
        SettingSwitch("اهتزاز المنبه", vibrate) { scope.launch { settings.setAlarmVibrate(it) } }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text("صوت المنبه"); OutlinedButton(onClick = { soundPicker.launch(Intent(RingtoneManager.ACTION_RINGTONE_PICKER).putExtra(RingtoneManager.EXTRA_RINGTONE_TYPE, RingtoneManager.TYPE_ALARM).putExtra(RingtoneManager.EXTRA_RINGTONE_EXISTING_URI, sound?.let(Uri::parse))) }) { Text("اختيار") } }
        HorizontalDivider()
        SettingSwitch("الوضع الداكن", dark) { scope.launch { settings.setDarkTheme(it) } }
        HorizontalDivider()
        Text("مفتاح API", style = MaterialTheme.typography.titleMedium)
        KeyStatusPanel(keyStatus, keyReason, keyFormat, keyModel, verifiedAt, storedKey, transientFailure, onRetry = { editing = true }, onCopyReport = { report ->
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("تقرير تشخيصي", report))
        })
        if (!editing && storedKey.isNotBlank()) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SecondaryButton("تغيير المفتاح", { editing = true }, modifier = Modifier.weight(1f))
                TextButton(onClick = { confirmDelete = true }, modifier = Modifier.weight(1f)) { Text("حذف المفتاح") }
            }
        } else {
            OutlinedTextField(value = key, onValueChange = { if (it.length <= 256) key = it }, modifier = Modifier.fillMaxWidth(), minLines = 1, maxLines = 3, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text), label = { Text("الصق مفتاح Gemini") })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                PrimaryButton(if (checking) "جارٍ التحقق" else "تحقق واحفظ", { scope.launch { checking = true; transientFailure = null; val result = ApiKeyVerifier().verifyOnce(key); checking = false; when (result) { is KeyCheckResult.Ok -> { settings.saveVerifiedApiKey(key, "OK_READY", "", result.format.code, result.model, result.verifiedAtMillis); editing = false }; is KeyCheckResult.Warning -> { settings.saveVerifiedApiKey(key, "QUOTA_LIMITED", result.reason.code, result.format.code, result.model, result.verifiedAtMillis); editing = false }; is KeyCheckResult.Failed -> transientFailure = result } } }, !checking, Modifier.weight(1f))
                TextButton(onClick = { key = storedKey; transientFailure = null; editing = false }, enabled = !checking, modifier = Modifier.weight(.5f)) { Text("إلغاء") }
            }
        }
        Button(onClick = onOpenDeviceCheck, modifier = Modifier.fillMaxWidth()) { Text("فحص الجهاز") }
        OutlinedButton(onClick = onClearData, modifier = Modifier.fillMaxWidth()) { Text("مسح البيانات") }
        Text("الإصدار ${com.adhanreminder.app.BuildConfig.VERSION_NAME} (${com.adhanreminder.app.BuildConfig.VERSION_CODE})", color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    if (confirmDelete) AlertDialog(onDismissRequest = { confirmDelete = false }, title = { Text("حذف المفتاح؟") }, text = { Text("سيُحذف المفتاح وحالة التحقق من الجهاز.") }, confirmButton = { Button(onClick = { confirmDelete = false; scope.launch { settings.clearApiKey(); key = ""; editing = true } }) { Text("نعم، احذف") } }, dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("إلغاء") } })
}

@Composable
private fun KeyStatusPanel(status: String, reason: String, format: String, model: String, verifiedAt: Long?, storedKey: String, failure: KeyCheckResult.Failed?, onRetry: () -> Unit, onCopyReport: (String) -> Unit) {
    val failed = failure ?: if (status == "FAILED") KeyCheckResult.Failed(reason.toFailureReason()) else null
    KeyCard {
        when {
            status == "NOT_SET" && failed == null -> StatusRow("—", "لم يُضَف مفتاح بعد")
            status == "OK_READY" -> { StatusRow("✓", "المفتاح يعمل", "تم التحقق من المفتاح والنموذج والخدمة."); KeyDetails(format, model, verifiedAt, storedKey) }
            status == "QUOTA_LIMITED" -> { StatusRow("⚠", "المفتاح صالح، لكن الحد الحالي مستنفد"); KeyDetails(format, model, verifiedAt, storedKey) }
            else -> { val actual = failed?.reason ?: FailureReason.UNKNOWN; StatusRow("✗", failureTitle(actual)); ReasonCode(actual.code.uppercase(Locale.ROOT)); Text(failureDetail(actual), color = MaterialTheme.colorScheme.onSurfaceVariant); LowPriorityButton("إعادة الاختبار", onRetry); LowPriorityButton("نسخ التقرير التشخيصي", onClick = { onCopyReport(diagnosticReport(status, reason, format, model, verifiedAt, storedKey, actual)) }) }
        }
    }
}

@Composable private fun KeyDetails(format: String, model: String, verifiedAt: Long?, key: String) { Text("النوع: ${formatLabel(format)}"); Text("النموذج: $model", fontFamily = PlexMono); Text("آخر تحقق: ${verifiedAt?.let { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).format(Date(it)) } ?: "—"}"); Text(maskKey(key), fontFamily = PlexMono) }
private fun formatLabel(value: String) = when (value) { KeyFormat.LEGACY_AIZA.code -> "صيغة قديمة (AIza)"; KeyFormat.NEW_AQ.code -> "صيغة حديثة (AQ.)"; else -> "صيغة غير معروفة، والاختبار هو المعيار" }
private fun String.toFailureReason() = FailureReason.entries.firstOrNull { it.code == lowercase(Locale.ROOT) } ?: FailureReason.UNKNOWN
private fun diagnosticReport(status: String, reason: String, format: String, model: String, verifiedAt: Long?, key: String, failure: FailureReason) = "Adhan Reminder\nstatus=$status\nreason=${failure.code.uppercase(Locale.ROOT)}\nformat=$format\nmodel=$model\nverifiedAt=${verifiedAt ?: "—"}\nkey=${maskKey(key)}"
private fun failureTitle(reason: FailureReason) = mapOf(FailureReason.EMPTY to "لم تُدخل مفتاحاً", FailureReason.INTERNAL_WHITESPACE to "المفتاح يحتوي مسافات أو أسطراً", FailureReason.KEY_INVALID to "المفتاح غير صحيح", FailureReason.MODEL_NOT_FOUND to "النموذج غير متاح", FailureReason.NO_NETWORK to "لا يوجد اتصال بالإنترنت", FailureReason.TIMEOUT to "انتهت مهلة الاتصال")[reason] ?: "خطأ غير مصنّف"
private fun failureDetail(reason: FailureReason) = when (reason) { FailureReason.EMPTY -> "الحقل فارغ."; FailureReason.INTERNAL_WHITESPACE -> "غالباً أُضيف نص زائد أثناء النسخ."; FailureReason.NO_NETWORK -> "تعذّر الوصول إلى خدمة Google."; FailureReason.TIMEOUT -> "الخدمة لم ترد في الوقت المحدد."; else -> "تعذر التحقق من المفتاح أو النموذج." }

@Composable private fun SettingStepper(title: String, value: Int, range: IntRange, onValue: (Int) -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(4.dp)) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title); Text(value.toString()) }; androidx.compose.material3.Slider(value = value.toFloat(), onValueChange = { onValue(it.toInt().coerceIn(range)) }, valueRange = range.first.toFloat()..range.last.toFloat(), steps = (range.last - range.first - 1).coerceAtLeast(0)) } }
@Composable private fun SettingSwitch(title: String, checked: Boolean, onChecked: (Boolean) -> Unit) { Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) { Text(title); androidx.compose.material3.Switch(checked, onCheckedChange = onChecked) } }
