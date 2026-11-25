package com.adhanreminder.app.ui.screens

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.adhanreminder.app.AdhanApplication
import com.adhanreminder.app.data.AppCheckStatus
import com.adhanreminder.app.data.AttemptDiagnostic
import com.adhanreminder.app.data.ServerProbeResult
import com.adhanreminder.app.data.local.MonthSchedule
import com.adhanreminder.app.data.local.PrayerDay
import com.adhanreminder.app.data.prepareForUpload
import com.adhanreminder.app.domain.AnalysisStage
import com.adhanreminder.app.domain.CaptureDiagnostics
import com.adhanreminder.app.domain.CapturePipeline
import com.adhanreminder.app.domain.CaptureSessionController
import com.adhanreminder.app.domain.PersistedDraftResult
import com.adhanreminder.app.domain.ScanQuotaPolicy
import com.adhanreminder.app.domain.ScanQuotaRecord
import com.adhanreminder.app.domain.ScanQuotaStatus
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import kotlin.coroutines.coroutineContext

sealed interface ServerProbeState {
    data object Idle : ServerProbeState
    data class Running(val completedAttempts: List<AttemptDiagnostic> = emptyList()) : ServerProbeState
    data class Done(val result: ServerProbeResult) : ServerProbeState
}

data class CaptureUiState(
    val selectedImageUri: Uri? = null,
    val stage: AnalysisStage = AnalysisStage.Idle,
    val quotaRecord: ScanQuotaRecord = ScanQuotaRecord(),
    val nowMillis: Long = System.currentTimeMillis(),
    val diagnostics: CaptureDiagnostics = CaptureDiagnostics(),
    val probeState: ServerProbeState = ServerProbeState.Idle,
) {
    val isBusy: Boolean get() = stage.isBusy
    val isProbing: Boolean get() = probeState is ServerProbeState.Running
    val quotaStatus: ScanQuotaStatus get() = ScanQuotaPolicy.evaluate(quotaRecord, nowMillis)
}

class CaptureViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as AdhanApplication

    private var stagedCameraFile: File? = null
    private var stagedCameraUri: Uri? = null
    private var activeCameraFile: File? = null
    private var probeJob: Job? = null

    private val session = CaptureSessionController<Uri>(
        scope = viewModelScope,
        pipelineFactory = { uri -> buildPipeline(uri) },
        appCheckProvider = { AppCheckStatus.current },
    )

    private val quotaRecordState = MutableStateFlow(ScanQuotaRecord())
    private val probeStateFlow = MutableStateFlow<ServerProbeState>(ServerProbeState.Idle)

    private val _uiState = MutableStateFlow(CaptureUiState())
    val uiState: StateFlow<CaptureUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            combine(session.state, quotaRecordState, probeStateFlow) { sessionState, quota, probe ->
                CaptureUiState(
                    selectedImageUri = sessionState.selectedImage,
                    stage = sessionState.stage,
                    quotaRecord = quota,
                    nowMillis = sessionState.nowMillis,
                    diagnostics = sessionState.diagnostics,
                    probeState = probe,
                )
            }.collect { combined ->
                _uiState.value = combined
            }
        }
        viewModelScope.launch {
            app.settings.ensureQuotaResetEpoch(QUOTA_RESET_EPOCH)
            app.settings.scanQuotaRecord.collect { record ->
                quotaRecordState.value = record
                session.refreshClock()
            }
        }
    }

    fun refreshClock() {
        session.refreshClock()
    }

    fun prepareCameraTarget(file: File, uri: Uri) {
        stagedCameraFile?.delete()
        stagedCameraFile = file
        stagedCameraUri = uri
    }

    fun onCameraResult(success: Boolean) {
        val uri = stagedCameraUri
        if (success && uri != null) {
            if (activeCameraFile != stagedCameraFile) {
                activeCameraFile?.delete()
            }
            activeCameraFile = stagedCameraFile
            stagedCameraFile = null
            stagedCameraUri = null
            session.selectImage(uri, autoStart = true) {
                activeCameraFile?.delete()
                activeCameraFile = null
            }
        } else {
            stagedCameraFile?.delete()
            stagedCameraFile = null
            stagedCameraUri = null
        }
    }

    fun onGalleryImageSelected(uri: Uri) {
        if (session.state.value.isBusy) return
        activeCameraFile?.delete()
        activeCameraFile = null
        session.selectImage(uri, autoStart = true)
    }

    fun onSampleTimetableSelected(uri: Uri) {
        onGalleryImageSelected(uri)
    }

    fun resetSelection() {
        if (session.resetSelection()) {
            activeCameraFile?.delete()
            activeCameraFile = null
        }
    }

    fun cancelAnalysis() {
        session.cancelAnalysis()
    }

    fun startAnalysis() {
        session.startAnalysis {
            activeCameraFile?.delete()
            activeCameraFile = null
        }
    }

    private fun buildPipeline(uri: Uri): CapturePipeline = CapturePipeline(
        prepareImage = { prepareForUpload(app.contentResolver, uri) },
        getQuotaRecord = { app.settings.scanQuotaRecord.first() },
        recordScanAttempt = { now, today -> app.settings.recordScanAttempt(now, today) },
        extractTimetable = { jpeg, onAttempt -> app.gemini.extractTimetable(jpeg, onAttempt) },
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
            PersistedDraftResult(scheduleId, validated.days.size)
        },
    )

    fun runServerProbe() {
        val current = _uiState.value
        if (current.isBusy || current.isProbing) return
        probeJob?.cancel()
        probeStateFlow.value = ServerProbeState.Running(emptyList())
        val job = viewModelScope.launch(start = CoroutineStart.LAZY) {
            val thisJob = coroutineContext[Job]
            val progressList = mutableListOf<AttemptDiagnostic>()
            try {
                val result = app.gemini.probeServer { attempt ->
                    if (probeJob === thisJob) {
                        progressList += attempt
                        probeStateFlow.value = ServerProbeState.Running(progressList.toList())
                    }
                }
                if (probeJob === thisJob) {
                    probeStateFlow.value = ServerProbeState.Done(result)
                }
            } finally {
                if (probeJob === thisJob) {
                    probeJob = null
                }
            }
        }
        probeJob = job
        job.start()
    }

    fun dismissProbeResult() {
        if (_uiState.value.isProbing) {
            probeJob?.cancel()
            probeJob = null
        }
        probeStateFlow.value = ServerProbeState.Idle
    }

    fun consumeSuccess() {
        session.consumeSuccess()
    }

    override fun onCleared() {
        super.onCleared()
        session.clear()
        probeJob?.cancel()
        probeJob = null
        stagedCameraFile?.delete()
        stagedCameraFile = null
        activeCameraFile?.delete()
        activeCameraFile = null
    }

    companion object {
        private const val QUOTA_RESET_EPOCH = 2
    }
}
