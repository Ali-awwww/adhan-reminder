package com.adhanreminder.app.ui.screens

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adhanreminder.app.AdhanApplication
import com.adhanreminder.app.data.AppCheckStatus
import com.adhanreminder.app.data.local.MonthSchedule
import com.adhanreminder.app.data.local.PrayerDay
import com.adhanreminder.app.data.prepareForUpload
import com.adhanreminder.app.domain.AnalysisStage
import com.adhanreminder.app.domain.CaptureDiagnostics
import com.adhanreminder.app.domain.CapturePipeline
import com.adhanreminder.app.domain.PersistedDraftResult
import com.adhanreminder.app.domain.ScanQuotaPolicy
import com.adhanreminder.app.domain.ScanQuotaRecord
import com.adhanreminder.app.domain.ScanQuotaStatus
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.coroutineContext

data class CaptureUiState(
    val selectedImageUri: Uri? = null,
    val stage: AnalysisStage = AnalysisStage.Idle,
    val quotaRecord: ScanQuotaRecord = ScanQuotaRecord(),
    val nowMillis: Long = System.currentTimeMillis(),
    val diagnostics: CaptureDiagnostics = CaptureDiagnostics(),
) {
    val isBusy: Boolean get() = stage.isBusy
    val quotaStatus: ScanQuotaStatus get() = ScanQuotaPolicy.evaluate(quotaRecord, nowMillis)
}

class CaptureViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AdhanApplication

    private var stagedCameraFile: File? = null
    private var stagedCameraUri: Uri? = null
    private var activeCameraFile: File? = null
    private var analysisJob: Job? = null

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            app.settings.scanQuotaRecord.collect { record ->
                _uiState.update { it.copy(quotaRecord = record, nowMillis = System.currentTimeMillis()) }
            }
        }
    }

    fun refreshClock() {
        _uiState.update { it.copy(nowMillis = System.currentTimeMillis()) }
    }

    fun prepareCameraTarget(file: File, uri: Uri) {
        stagedCameraFile?.delete()
        stagedCameraFile = file
        stagedCameraUri = uri
    }

    fun onCameraResult(success: Boolean) {
        if (success && stagedCameraUri != null) {
            if (activeCameraFile != stagedCameraFile) {
                activeCameraFile?.delete()
            }
            activeCameraFile = stagedCameraFile
            val uri = stagedCameraUri
            stagedCameraFile = null
            stagedCameraUri = null
            _uiState.update {
                it.copy(
                    selectedImageUri = uri,
                    stage = AnalysisStage.PreviewReady,
                    diagnostics = CaptureDiagnostics(),
                )
            }
        } else {
            stagedCameraFile?.delete()
            stagedCameraFile = null
            stagedCameraUri = null
        }
    }

    fun onGalleryImageSelected(uri: Uri) {
        if (_uiState.value.isBusy) return
        activeCameraFile?.delete()
        activeCameraFile = null
        _uiState.update {
            it.copy(
                selectedImageUri = uri,
                stage = AnalysisStage.PreviewReady,
                diagnostics = CaptureDiagnostics(),
            )
        }
    }

    fun onSampleTimetableSelected(uri: Uri) {
        onGalleryImageSelected(uri)
    }

    fun resetSelection() {
        if (_uiState.value.isBusy) return
        activeCameraFile?.delete()
        activeCameraFile = null
        _uiState.update {
            it.copy(
                selectedImageUri = null,
                stage = AnalysisStage.Idle,
                diagnostics = CaptureDiagnostics(),
            )
        }
    }

    fun cancelAnalysis() {
        val current = _uiState.value
        if (!current.isBusy) return
        analysisJob?.cancel()
        analysisJob = null
        val now = System.currentTimeMillis()
        val elapsed = current.diagnostics.aiStartedAtMillis?.let { (now - it).coerceAtLeast(0L) }
        _uiState.update { state ->
            state.copy(
                stage = AnalysisStage.PreviewReady,
                nowMillis = now,
                diagnostics = state.diagnostics.copy(
                    isCancelled = true,
                    latencyMillis = elapsed ?: state.diagnostics.latencyMillis,
                    statusDetail = "تم إلغاء الطلب بواسطة المستخدم",
                ),
            )
        }
    }

    fun startAnalysis() {
        val current = _uiState.value
        val uri = current.selectedImageUri ?: return
        if (current.isBusy || current.stage is AnalysisStage.Success) return

        val appCheck = AppCheckStatus.current
        val initialDiagnostics = CaptureDiagnostics(
            appCheckLabel = appCheck.labelAr,
            appCheckDetail = appCheck.detailAr,
            statusDetail = "بدء تجهيز الصورة…",
        )
        _uiState.update {
            it.copy(
                diagnostics = initialDiagnostics,
                nowMillis = System.currentTimeMillis(),
            )
        }

        analysisJob?.cancel()
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val thisJob = coroutineContext[Job]
            val pipeline = CapturePipeline(
                prepareImage = { prepareForUpload(app.contentResolver, uri) },
                getQuotaRecord = { app.settings.scanQuotaRecord.first() },
                recordScanAttempt = { now, today -> app.settings.recordScanAttempt(now, today) },
                extractTimetable = { jpeg -> app.gemini.extractTimetable(jpeg) },
                saveValidatedSchedule = { parsed, validated ->
                    val label = listOfNotNull(parsed.hijriMonthName, parsed.hijriYear?.toString())
                        .joinToString(" ")
                        .ifBlank { "جدول جديد" }
                    val scheduleId = app.repository.insertSchedule(
                        MonthSchedule(
                            hijriMonthLabel = label,
                            createdAtMillis = System.currentTimeMillis(),
                            sourceImagePath = null,
                        )
                    )
                    app.repository.insertPrayerDays(validated.days.map { day ->
                        PrayerDay(
                            scheduleId = scheduleId,
                            gregorianDate = day.gregorianDate,
                            hijriDay = day.hijriDay,
                            fajr = day.times.fajr.orEmpty(),
                            dhuhr = day.times.dhuhr.orEmpty(),
                            asr = day.times.asr.orEmpty(),
                            maghrib = day.times.maghrib.orEmpty(),
                            isha = day.times.isha.orEmpty(),
                            reviewFlagsJson = JSONArray(day.flags.map {
                                JSONObject()
                                    .put("gregorianDate", it.gregorianDate)
                                    .put("field", it.field)
                                    .put("reason", it.reason)
                            }).toString(),
                        )
                    })
                    val extractedNote = parsed.fajrNoteMinutes
                    if (extractedNote != null && extractedNote in 0..60) {
                        app.settings.setPendingFajrNote(scheduleId, extractedNote)
                    }
                    PersistedDraftResult(scheduleId, validated.days.size)
                },
            )

            try {
                val finalStage = pipeline.execute(
                    initialDiagnostics = initialDiagnostics,
                    onDiagnostics = { diag ->
                        if (analysisJob === thisJob) {
                            _uiState.update { state ->
                                state.copy(
                                    diagnostics = diag.copy(
                                        appCheckLabel = appCheck.labelAr,
                                        appCheckDetail = appCheck.detailAr,
                                    ),
                                    nowMillis = System.currentTimeMillis(),
                                )
                            }
                        }
                    },
                    onStage = { stage ->
                        if (analysisJob === thisJob) {
                            _uiState.update { it.copy(stage = stage, nowMillis = System.currentTimeMillis()) }
                        }
                    },
                )
                if (finalStage is AnalysisStage.Success && analysisJob === thisJob) {
                    activeCameraFile?.delete()
                    activeCameraFile = null
                }
            } finally {
                if (analysisJob === thisJob) {
                    analysisJob = null
                }
            }
        }
        analysisJob = job
        job.start()
    }

    fun consumeSuccess() {
        _uiState.update {
            it.copy(
                selectedImageUri = null,
                stage = AnalysisStage.Idle,
                diagnostics = CaptureDiagnostics(),
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        analysisJob?.cancel()
        analysisJob = null
        stagedCameraFile?.delete()
        stagedCameraFile = null
        activeCameraFile?.delete()
        activeCameraFile = null
    }
}
