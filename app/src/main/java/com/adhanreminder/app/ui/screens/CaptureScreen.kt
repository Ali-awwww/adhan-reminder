package com.adhanreminder.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.adhanreminder.app.AdhanApplication
import com.adhanreminder.app.data.ImagePreparationResult
import com.adhanreminder.app.data.prepareForUpload
import com.adhanreminder.app.data.local.MonthSchedule
import com.adhanreminder.app.data.local.PrayerDay
import com.adhanreminder.app.data.TimetableResult
import com.adhanreminder.app.domain.ExtractionParser
import com.adhanreminder.app.domain.TimetableValidator
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

@Composable
fun CaptureScreen(onBack: () -> Unit, onScheduleCreated: (Long) -> Unit) {
    val context = LocalContext.current
    val app = context.applicationContext as AdhanApplication
    val scope = rememberCoroutineScope()
    var imageUri by remember { mutableStateOf<android.net.Uri?>(null) }
    var cameraFile by remember { mutableStateOf<File?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        if (!success) {
            cameraFile?.delete()
            cameraFile = null
            imageUri = null
        }
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) imageUri = uri }

    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("التصوير", style = MaterialTheme.typography.titleLarge)
        if (imageUri == null) {
            Button(onClick = {
                val file = File(context.cacheDir, "images/timetable_${System.currentTimeMillis()}.jpg").apply { parentFile?.mkdirs() }
                cameraFile = file
                imageUri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                cameraLauncher.launch(imageUri!!)
            }, modifier = Modifier.fillMaxWidth()) { Text("تصوير الجدول") }
            OutlinedButton(onClick = { galleryLauncher.launch(androidx.activity.result.PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxWidth()) { Text("اختيار من المعرض") }
        } else {
            androidx.compose.ui.viewinterop.AndroidView(
                factory = { android.widget.ImageView(it).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER } },
                update = { it.setImageURI(imageUri) },
                modifier = Modifier.fillMaxWidth().heightIn(max = 480.dp),
            )
            Text("الجدول كله داخل الإطار، والضوء من الأمام", color = MaterialTheme.colorScheme.onSurfaceVariant)
            RowButtons(
                onRetry = {
                    cameraFile?.delete()
                    cameraFile = null
                    imageUri = null
                    message = null
                },
                onSend = {
                    val uri = imageUri ?: return@RowButtons
                    scope.launch {
                        busy = true
                        message = null
                        try {
                            val prepared = prepareForUpload(context.contentResolver, uri)
                            if (prepared !is ImagePreparationResult.Success) {
                                message = "تعذّر فتح الصورة أو أن حجمها كبير، جرّب صورة أخرى"
                                return@launch
                            }
                            val result = app.gemini.extractTimetable(prepared.jpeg)
                            val raw = (result as? TimetableResult.Success)?.rawJson
                            if (raw == null) {
                                message = result.userMessage
                                return@launch
                            }
                            val parsed = ExtractionParser.parse(raw).getOrElse {
                                message = when (it) {
                                    is com.adhanreminder.app.domain.ExtractionFailure.NotAPrayerTimetable -> "هذه الصورة لا تبدو جدول مواقيت. صوّر الجدول كاملاً."
                                    is com.adhanreminder.app.domain.ExtractionFailure.InvalidDayCount -> "عدد أيام الجدول غير صالح."
                                    else -> "تعذّر تحليل الجدول، راجع الصورة وحاول مجدداً."
                                }
                                return@launch
                            }
                            val validated = TimetableValidator.validate(parsed)
                            val label = listOfNotNull(parsed.hijriMonthName, parsed.hijriYear?.toString()).joinToString(" ").ifBlank { "جدول جديد" }
                            val scheduleId = app.repository.insertSchedule(MonthSchedule(hijriMonthLabel = label, createdAtMillis = System.currentTimeMillis(), sourceImagePath = null))
                            app.repository.insertPrayerDays(validated.days.map { day ->
                                PrayerDay(scheduleId = scheduleId, gregorianDate = day.gregorianDate, hijriDay = day.hijriDay, fajr = day.times.fajr.orEmpty(), dhuhr = day.times.dhuhr.orEmpty(), asr = day.times.asr.orEmpty(), maghrib = day.times.maghrib.orEmpty(), isha = day.times.isha.orEmpty(), reviewFlagsJson = JSONArray(day.flags.map { JSONObject().put("gregorianDate", it.gregorianDate).put("field", it.field).put("reason", it.reason) }).toString())
                            })
                            onScheduleCreated(scheduleId)
                        } catch (_: Exception) {
                            message = "تعذّرت قراءة الجدول. تحقق من الاتصال والصورة وحاول مجدداً."
                        } finally {
                            cameraFile?.delete()
                            cameraFile = null
                            busy = false
                        }
                    }
                },
                busy = busy,
            )
        }
        message?.let { Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        OutlinedButton(onClick = onBack, enabled = !busy) { Text("رجوع") }
    }
}

@Composable
private fun RowButtons(onRetry: () -> Unit, onSend: () -> Unit, busy: Boolean) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedButton(onClick = onRetry, enabled = !busy, modifier = Modifier.weight(1f)) { Text("إعادة التصوير") }
        Button(onClick = onSend, enabled = !busy, modifier = Modifier.weight(1f)) { Text(if (busy) "جارٍ القراءة…" else "إرسال للقراءة") }
    }
}
