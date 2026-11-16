package com.adhanreminder.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.adhanreminder.app.data.AppCheckStatus
import com.adhanreminder.app.data.AttemptDiagnostic
import com.adhanreminder.app.data.ModelConfig
import com.adhanreminder.app.domain.AnalysisStage
import com.adhanreminder.app.domain.CaptureDiagnostics
import com.adhanreminder.app.ui.screens.ServerProbeState
import com.adhanreminder.app.ui.theme.PlexMono
import java.util.Locale

@Composable
fun CaptureDiagnosticsCard(
    stage: AnalysisStage,
    diagnostics: CaptureDiagnostics,
    elapsedSeconds: Int,
    timeoutSeconds: Int,
) {
    val appCheckText = diagnostics.appCheckLabel.ifBlank { AppCheckStatus.current.labelAr }
    val activeModel = diagnostics.activeModelName.ifBlank { ModelConfig.MODEL_NAME }
    val imageText = when {
        diagnostics.imageBytes != null -> {
            val kb = maxOf(1, (diagnostics.imageBytes + 512) / 1024)
            "$kb KB · JPEG"
        }
        stage is AnalysisStage.PreparingImage -> "جارٍ ضغط الصورة…"
        else -> "—"
    }
    val connectionText = when {
        stage is AnalysisStage.AnalyzingWithAi ->
            "في انتظار رد $activeModel (${diagnostics.activeMode.labelAr}) · ${elapsedSeconds}ث / ${timeoutSeconds}ث"
        diagnostics.isCancelled -> "أُلغي يدوياً بعد ${elapsedSeconds}ث"
        diagnostics.latencyMillis != null ->
            "استغرق ${String.format(Locale.US, "%.1f", diagnostics.latencyMillis / 1000.0)}ث"
        stage is AnalysisStage.PreparingImage -> "تجهيز محلي قبل الإرسال"
        else -> "جاهز"
    }
    val responseText = when {
        stage is AnalysisStage.Error && diagnostics.responseChars != null -> {
            val detail = diagnostics.statusDetail ?: "فشل التحليل"
            "استلم ${diagnostics.responseChars} حرفاً · $detail"
        }
        diagnostics.responseChars != null -> {
            val latencyPart = diagnostics.latencyMillis?.let {
                " خلال ${String.format(Locale.US, "%.1f", it / 1000.0)}ث"
            }.orEmpty()
            "استلم ${diagnostics.responseChars} حرفاً من $activeModel (${diagnostics.activeMode.labelAr})$latencyPart"
        }
        diagnostics.statusDetail != null -> diagnostics.statusDetail
        else -> "—"
    }
    val responseIsError = stage is AnalysisStage.Error || diagnostics.isCancelled
    val headerModelBadge = buildString {
        append(activeModel)
        append(" · ")
        append(diagnostics.activeMode.labelAr)
        if (diagnostics.usedFallbackModel) append(" (بديل)")
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("capture_diagnostics_card"),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "تشخيص الإرسال المباشر",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                Text(
                    headerModelBadge,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = PlexMono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DiagnosticRow(label = "App Check", value = appCheckText)
            DiagnosticRow(label = "الصورة المرسلة", value = imageText, mono = true)
            DiagnosticRow(label = "الاتصال والزمن", value = connectionText, mono = stage is AnalysisStage.AnalyzingWithAi)
            DiagnosticRow(
                label = "النتيجة / الخطأ الحرفي",
                value = responseText,
                mono = responseIsError,
                isError = responseIsError,
            )
            if (stage is AnalysisStage.Error && !diagnostics.responsePreview.isNullOrBlank()) {
                DiagnosticRow(
                    label = "مقتطف الرد",
                    value = diagnostics.responsePreview,
                    mono = true,
                    isError = true,
                )
            }
            if (diagnostics.attempts.isNotEmpty()) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Text(
                    "سجل المحاولات المفصل",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    diagnostics.attempts.forEachIndexed { index, attempt ->
                        AttemptRow(index = index + 1, attempt = attempt)
                    }
                }
            }
            diagnostics.appCheckDetail?.takeIf { it.isNotBlank() }?.let { note ->
                Text(
                    note,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun ServerProbeCard(
    probeState: ServerProbeState,
    onDismiss: () -> Unit,
) {
    if (probeState is ServerProbeState.Idle) return

    val attempts = when (probeState) {
        is ServerProbeState.Running -> probeState.completedAttempts
        is ServerProbeState.Done -> probeState.result.attempts
        ServerProbeState.Idle -> emptyList()
    }
    val appCheckLabel = when (probeState) {
        is ServerProbeState.Done -> probeState.result.appCheckLabel
        else -> AppCheckStatus.current.labelAr
    }

    Card(
        modifier = Modifier.fillMaxWidth().testTag("capture_probe_card"),
        border = BorderStroke(
            width = 1.dp,
            color = when (probeState) {
                is ServerProbeState.Done -> if (probeState.result.anySucceeded) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.error
                }
                else -> MaterialTheme.colorScheme.outline
            },
        ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = if (probeState is ServerProbeState.Running) {
                        "جارٍ فحص الخادم (طلب نصي بدون رصيد)…"
                    } else {
                        "نتيجة فحص اتصال الخادم"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Medium,
                )
                if (probeState is ServerProbeState.Done) {
                    TextButton(
                        onClick = onDismiss,
                        modifier = Modifier.heightIn(min = 48.dp).testTag("capture_probe_dismiss_button"),
                    ) {
                        Text("إخفاء")
                    }
                }
            }

            DiagnosticRow(label = "App Check", value = appCheckLabel)

            if (attempts.isEmpty() && probeState is ServerProbeState.Running) {
                Text(
                    "يتم اختبار ${ModelConfig.MODEL_NAME} ثم ${ModelConfig.FALLBACK_MODEL_NAME}…",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    attempts.forEachIndexed { index, attempt ->
                        AttemptRow(index = index + 1, attempt = attempt)
                    }
                }
            }
        }
    }
}

@Composable
private fun AttemptRow(index: Int, attempt: AttemptDiagnostic) {
    val icon = if (attempt.succeeded) "✓" else "✕"
    val color = if (attempt.succeeded) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    val secText = String.format(Locale.US, "%.1fث", attempt.elapsedMillis / 1000.0)
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                "$index. $icon ${attempt.modelName} (${attempt.mode.labelAr})",
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = PlexMono, fontWeight = FontWeight.Medium),
                color = color,
            )
            Text(
                secText,
                style = MaterialTheme.typography.bodySmall.copy(fontFamily = PlexMono),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            attempt.rawDetail,
            style = MaterialTheme.typography.bodySmall.copy(fontFamily = PlexMono),
            color = if (attempt.succeeded) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun DiagnosticRow(
    label: String,
    value: String,
    mono: Boolean = false,
    isError: Boolean = false,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 12.dp),
        )
        Text(
            value,
            style = if (mono) {
                MaterialTheme.typography.bodySmall.copy(fontFamily = PlexMono)
            } else {
                MaterialTheme.typography.bodySmall
            },
            color = if (isError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
    }
}
