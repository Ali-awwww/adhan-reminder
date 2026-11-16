package com.adhanreminder.app.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.adhanreminder.app.R
import com.adhanreminder.app.data.AppCheckStatus
import com.adhanreminder.app.data.GeminiClient
import com.adhanreminder.app.data.ModelConfig
import com.adhanreminder.app.domain.AnalysisStage
import com.adhanreminder.app.domain.CaptureDiagnostics
import com.adhanreminder.app.domain.ScanQuotaPolicy
import com.adhanreminder.app.ui.theme.PlexMono
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

@Composable
fun CaptureScreen(
    onBack: () -> Unit,
    onScheduleCreated: (Long) -> Unit,
    vm: CaptureViewModel = viewModel(),
) {
    val context = LocalContext.current
    val uiState by vm.uiState.collectAsStateWithLifecycle()
    val stage = uiState.stage
    val quotaStatus = uiState.quotaStatus
    val diagnostics = uiState.diagnostics
    val timeoutSeconds = (GeminiClient.REQUEST_TIMEOUT_MS / 1_000L).toInt()
    val elapsedSeconds = diagnostics.aiStartedAtMillis?.let { started ->
        ((uiState.nowMillis - started).coerceAtLeast(0L) / 1_000L).toInt().coerceAtMost(timeoutSeconds)
    } ?: 0

    LaunchedEffect(
        uiState.isBusy,
        uiState.quotaRecord.lastAttemptMillis,
        quotaStatus.cooldownRemainingSeconds,
    ) {
        while (
            uiState.isBusy ||
            ScanQuotaPolicy.evaluate(uiState.quotaRecord, System.currentTimeMillis()).cooldownRemainingSeconds > 0
        ) {
            delay(1_000L)
            vm.refreshClock()
        }
        vm.refreshClock()
    }

    LaunchedEffect(stage) {
        if (stage is AnalysisStage.Success) {
            delay(450L)
            onScheduleCreated(stage.scheduleId)
            vm.consumeSuccess()
        }
    }

    val cameraLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { success ->
        vm.onCameraResult(success)
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.onGalleryImageSelected(uri)
    }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("التصوير", style = MaterialTheme.typography.titleLarge)

        Card(
            modifier = Modifier.fillMaxWidth().testTag("capture_quota_card"),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        ) {
            Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "رصيد القراءة المتبقي: اليوم ${quotaStatus.dailyRemaining}/${ScanQuotaPolicy.MAX_DAILY_SCANS} · هذا الشهر ${quotaStatus.monthlyRemaining}/${ScanQuotaPolicy.MAX_MONTHLY_SCANS}",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!uiState.isBusy && quotaStatus.blockReasonAr != null) {
                    Text(
                        quotaStatus.blockReasonAr!!,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }

        val imageUri = uiState.selectedImageUri
        if (imageUri == null) {
            Button(
                onClick = {
                    val file = File(context.cacheDir, "images/timetable_${System.currentTimeMillis()}.jpg")
                        .apply { parentFile?.mkdirs() }
                    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                    vm.prepareCameraTarget(file, uri)
                    cameraLauncher.launch(uri)
                },
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("capture_take_photo_button"),
            ) { Text("تصوير الجدول") }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("capture_pick_gallery_button"),
                ) {
                    Text("اختيار من المعرض", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }

                OutlinedButton(
                    onClick = {
                        val sampleUri = Uri.parse("android.resource://${context.packageName}/${R.drawable.sample_timetable}")
                        vm.onSampleTimetableSelected(sampleUri)
                    },
                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 12.dp),
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("capture_sample_timetable_button"),
                ) {
                    Text("تجربة جدول جاهز", maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
        } else {
            TimetablePreviewWithScanner(
                imageUri = imageUri,
                isScanning = uiState.isBusy,
                isSuccess = stage is AnalysisStage.Success,
            )

            if (stage is AnalysisStage.PreviewReady && !diagnostics.isCancelled) {
                Text(
                    "الجدول كله داخل الإطار، والضوء من الأمام",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            AnimatedVisibility(
                visible = uiState.isBusy || stage is AnalysisStage.Success,
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(180)),
            ) {
                AnalysisProgressCard(
                    stage = stage,
                    elapsedSeconds = elapsedSeconds,
                    timeoutSeconds = timeoutSeconds,
                )
            }

            val showDiagnostics = uiState.isBusy ||
                stage is AnalysisStage.Success ||
                stage is AnalysisStage.Error ||
                diagnostics.isCancelled ||
                diagnostics.imageBytes != null ||
                diagnostics.statusDetail != null

            AnimatedVisibility(
                visible = showDiagnostics,
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(180)),
            ) {
                CaptureDiagnosticsCard(
                    stage = stage,
                    diagnostics = diagnostics,
                    elapsedSeconds = elapsedSeconds,
                    timeoutSeconds = timeoutSeconds,
                )
            }

            AnimatedVisibility(
                visible = stage is AnalysisStage.Error,
                enter = fadeIn(tween(220)),
                exit = fadeOut(tween(180)),
            ) {
                val errorStage = stage as? AnalysisStage.Error
                if (errorStage != null) {
                    AnalysisErrorCard(
                        message = errorStage.message,
                        canRetry = errorStage.canRetryWithSameImage && quotaStatus.canScan,
                        cooldownSeconds = quotaStatus.cooldownRemainingSeconds,
                        onRetryAnalysis = { vm.startAnalysis() },
                        onChangeImage = { vm.resetSelection() },
                    )
                }
            }

            if (stage is AnalysisStage.PreviewReady || uiState.isBusy) {
                RowButtons(
                    onRetry = { vm.resetSelection() },
                    onSend = { vm.startAnalysis() },
                    onCancel = { vm.cancelAnalysis() },
                    busy = uiState.isBusy,
                    canSend = quotaStatus.canScan,
                    cooldownSeconds = quotaStatus.cooldownRemainingSeconds,
                    elapsedSeconds = elapsedSeconds,
                )
            }
        }

        OutlinedButton(
            onClick = onBack,
            enabled = !uiState.isBusy,
            modifier = Modifier.heightIn(min = 48.dp).testTag("capture_back_button"),
        ) { Text("رجوع") }
    }
}

@Composable
private fun TimetablePreviewWithScanner(
    imageUri: android.net.Uri,
    isScanning: Boolean,
    isSuccess: Boolean,
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan_beam_transition")
    val beamFraction by infiniteTransition.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.94f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1550, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "scan_beam_fraction",
    )
    val beamColor = MaterialTheme.colorScheme.primary
    val borderColor = if (isSuccess) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 200.dp, max = 400.dp)
            .clip(RoundedCornerShape(12.dp))
            .testTag("capture_preview_container"),
    ) {
        androidx.compose.ui.viewinterop.AndroidView(
            factory = { android.widget.ImageView(it).apply { scaleType = android.widget.ImageView.ScaleType.FIT_CENTER } },
            update = { it.setImageURI(imageUri) },
            modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp),
        )

        Canvas(modifier = Modifier.matchParentSize()) {
            val strokePx = 2.5.dp.toPx()
            val bracketLen = 22.dp.toPx().coerceAtMost(size.minDimension * 0.18f)

            // Corner viewfinder brackets
            drawLine(borderColor, Offset(0f, 0f), Offset(bracketLen, 0f), strokePx, StrokeCap.Round)
            drawLine(borderColor, Offset(0f, 0f), Offset(0f, bracketLen), strokePx, StrokeCap.Round)
            drawLine(borderColor, Offset(size.width, 0f), Offset(size.width - bracketLen, 0f), strokePx, StrokeCap.Round)
            drawLine(borderColor, Offset(size.width, 0f), Offset(size.width, bracketLen), strokePx, StrokeCap.Round)
            drawLine(borderColor, Offset(0f, size.height), Offset(bracketLen, size.height), strokePx, StrokeCap.Round)
            drawLine(borderColor, Offset(0f, size.height), Offset(0f, size.height - bracketLen), strokePx, StrokeCap.Round)
            drawLine(borderColor, Offset(size.width, size.height), Offset(size.width - bracketLen, size.height), strokePx, StrokeCap.Round)
            drawLine(borderColor, Offset(size.width, size.height), Offset(size.width, size.height - bracketLen), strokePx, StrokeCap.Round)

            if (isScanning) {
                drawRect(Color.Black.copy(alpha = 0.28f))
                val y = (beamFraction * size.height).coerceIn(0f, size.height)
                val glowHalf = 28.dp.toPx()
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            beamColor.copy(alpha = 0.28f),
                            Color.Transparent,
                        ),
                        startY = (y - glowHalf).coerceAtLeast(0f),
                        endY = (y + glowHalf).coerceAtMost(size.height),
                    ),
                )
                drawLine(
                    color = beamColor,
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 3.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}

@Composable
private fun AnalysisProgressCard(
    stage: AnalysisStage,
    elapsedSeconds: Int,
    timeoutSeconds: Int,
) {
    val targetProgress = when (stage) {
        is AnalysisStage.PreparingImage -> 0.28f
        is AnalysisStage.AnalyzingWithAi -> 0.68f
        is AnalysisStage.ValidatingSchedule -> 0.92f
        is AnalysisStage.Success -> 1f
        else -> 0f
    }
    val animatedProgress by animateFloatAsState(
        targetValue = targetProgress,
        animationSpec = spring(stiffness = 220f),
        label = "analysis_progress",
    )

    val activeStep = when (stage) {
        is AnalysisStage.PreparingImage -> 0
        is AnalysisStage.AnalyzingWithAi -> 1
        is AnalysisStage.ValidatingSchedule -> 2
        is AnalysisStage.Success -> 3
        else -> -1
    }

    val stageAnnouncement = when (stage) {
        is AnalysisStage.PreparingImage -> "تجهيز وضغط الصورة"
        is AnalysisStage.AnalyzingWithAi -> "قراءة المواقيت بالذكاء الاصطناعي · ${elapsedSeconds}ث / ${timeoutSeconds}ث"
        is AnalysisStage.ValidatingSchedule -> "التحقق من الأوقات وحفظ المسودة"
        is AnalysisStage.Success -> "تمت قراءة ${stage.dayCount} يوماً بنجاح"
        else -> ""
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .semantics {
                liveRegion = LiveRegionMode.Polite
                contentDescription = stageAnnouncement
            }
            .testTag("capture_progress_card"),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            AnimatedContent(
                targetState = stageAnnouncement,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(150)) },
                label = "stage_title_transition",
            ) { title ->
                Text(title, style = MaterialTheme.typography.titleSmall)
            }
            LinearProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxWidth().testTag("capture_progress_bar"),
            )
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                StepRow("تجهيز وضغط الصورة", stepIndex = 0, activeStep = activeStep)
                StepRow("قراءة المواقيت بالذكاء الاصطناعي", stepIndex = 1, activeStep = activeStep)
                StepRow("التحقق من الأوقات وحفظ المسودة", stepIndex = 2, activeStep = activeStep)
            }
        }
    }
}

@Composable
private fun CaptureDiagnosticsCard(
    stage: AnalysisStage,
    diagnostics: CaptureDiagnostics,
    elapsedSeconds: Int,
    timeoutSeconds: Int,
) {
    val appCheckText = diagnostics.appCheckLabel.ifBlank { AppCheckStatus.current.labelAr }
    val imageText = when {
        diagnostics.imageBytes != null -> {
            val kb = maxOf(1, (diagnostics.imageBytes + 512) / 1024)
            "$kb KB · JPEG"
        }
        stage is AnalysisStage.PreparingImage -> "جارٍ ضغط الصورة…"
        else -> "—"
    }
    val connectionText = when {
        stage is AnalysisStage.AnalyzingWithAi -> "في انتظار رد ${ModelConfig.MODEL_NAME} · ${elapsedSeconds}ث / ${timeoutSeconds}ث"
        diagnostics.isCancelled -> "أُلغي يدوياً بعد ${elapsedSeconds}ث"
        diagnostics.latencyMillis != null -> "استغرق ${String.format(Locale.US, "%.1f", diagnostics.latencyMillis / 1000.0)}ث"
        stage is AnalysisStage.PreparingImage -> "تجهيز محلي قبل الإرسال"
        else -> "جاهز"
    }
    val responseText = when {
        diagnostics.responseChars != null -> {
            val latencyPart = diagnostics.latencyMillis?.let {
                " خلال ${String.format(Locale.US, "%.1f", it / 1000.0)}ث"
            }.orEmpty()
            "استلم ${diagnostics.responseChars} حرفاً$latencyPart"
        }
        diagnostics.statusDetail != null -> diagnostics.statusDetail
        else -> "—"
    }
    val responseIsError = stage is AnalysisStage.Error || diagnostics.isCancelled

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
                    ModelConfig.MODEL_NAME,
                    style = MaterialTheme.typography.bodySmall.copy(fontFamily = PlexMono),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            DiagnosticRow(label = "App Check", value = appCheckText)
            DiagnosticRow(label = "الصورة المرسلة", value = imageText, mono = true)
            DiagnosticRow(label = "الاتصال والزمن", value = connectionText, mono = stage is AnalysisStage.AnalyzingWithAi)
            DiagnosticRow(
                label = "حالة الرد",
                value = responseText,
                isError = responseIsError,
            )
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

@Composable
private fun StepRow(label: String, stepIndex: Int, activeStep: Int) {
    val icon = when {
        activeStep > stepIndex -> "✓"
        activeStep == stepIndex -> "●"
        else -> "○"
    }
    val color = when {
        activeStep >= stepIndex -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(icon, color = color, style = MaterialTheme.typography.bodySmall)
        Text(label, color = color, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun AnalysisErrorCard(
    message: String,
    canRetry: Boolean,
    cooldownSeconds: Int,
    onRetryAnalysis: () -> Unit,
    onChangeImage: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth().testTag("capture_error_card"),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "تعذّرت قراءة الجدول",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.error,
            )
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag("capture_error_message"),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (canRetry) {
                    Button(
                        onClick = onRetryAnalysis,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("capture_retry_send_button"),
                    ) { Text("إعادة المحاولة") }
                } else if (cooldownSeconds > 0) {
                    Button(
                        onClick = {},
                        enabled = false,
                        modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("capture_cooldown_retry_button"),
                    ) { Text("انتظر ${cooldownSeconds}ث") }
                }
                OutlinedButton(
                    onClick = onChangeImage,
                    modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("capture_change_image_button"),
                ) { Text("تغيير الصورة") }
            }
        }
    }
}

@Composable
private fun RowButtons(
    onRetry: () -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    busy: Boolean,
    canSend: Boolean,
    cooldownSeconds: Int,
    elapsedSeconds: Int,
) {
    val sendLabel = when {
        busy -> "جارٍ التحليل… (${elapsedSeconds}ث)"
        cooldownSeconds > 0 -> "انتظر ${cooldownSeconds}ث"
        else -> "إرسال للقراءة"
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        if (busy) {
            OutlinedButton(
                onClick = onCancel,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("capture_cancel_analysis_button"),
            ) {
                Text("إلغاء القراءة", color = MaterialTheme.colorScheme.error)
            }
        } else {
            OutlinedButton(
                onClick = onRetry,
                modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("capture_retry_button"),
            ) { Text("إعادة التصوير") }
        }
        Button(
            onClick = onSend,
            enabled = !busy && canSend,
            modifier = Modifier.weight(1f).heightIn(min = 48.dp).testTag("capture_send_button"),
        ) { Text(sendLabel) }
    }
}
