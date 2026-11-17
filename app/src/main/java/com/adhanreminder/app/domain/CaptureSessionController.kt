package com.adhanreminder.app.domain

import com.adhanreminder.app.data.AppCheckDiagnostics
import com.adhanreminder.app.data.AppCheckStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.coroutines.coroutineContext

data class CaptureSessionState<ImageRef : Any>(
    val selectedImage: ImageRef? = null,
    val stage: AnalysisStage = AnalysisStage.Idle,
    val diagnostics: CaptureDiagnostics = CaptureDiagnostics(),
    val nowMillis: Long = 0L,
) {
    val isBusy: Boolean get() = stage.isBusy
}

class CaptureSessionController<ImageRef : Any>(
    private val scope: CoroutineScope,
    private val pipelineFactory: (ImageRef) -> CapturePipeline,
    private val appCheckProvider: () -> AppCheckDiagnostics = { AppCheckStatus.current },
    private val nowMillisProvider: () -> Long = { System.currentTimeMillis() },
) {
    private var analysisJob: Job? = null

    private val _state = MutableStateFlow(
        CaptureSessionState<ImageRef>(nowMillis = nowMillisProvider()),
    )
    val state: StateFlow<CaptureSessionState<ImageRef>> = _state.asStateFlow()

    fun refreshClock() {
        _state.update { it.copy(nowMillis = nowMillisProvider()) }
    }

    fun selectImage(
        image: ImageRef,
        autoStart: Boolean = true,
        onSuccess: (() -> Unit)? = null,
    ): Boolean {
        if (_state.value.isBusy) return false
        _state.update {
            it.copy(
                selectedImage = image,
                stage = AnalysisStage.PreviewReady,
                diagnostics = CaptureDiagnostics(),
                nowMillis = nowMillisProvider(),
            )
        }
        if (autoStart) {
            startAnalysis(onSuccess)
        }
        return true
    }

    fun startAnalysis(onSuccess: (() -> Unit)? = null) {
        val current = _state.value
        val image = current.selectedImage ?: return
        if (current.isBusy || current.stage is AnalysisStage.Success) return

        val appCheck = appCheckProvider()
        val initialDiagnostics = CaptureDiagnostics(
            appCheckLabel = appCheck.labelAr,
            appCheckDetail = appCheck.detailAr,
            statusDetail = "بدء تجهيز الصورة…",
        )
        _state.update {
            it.copy(
                diagnostics = initialDiagnostics,
                nowMillis = nowMillisProvider(),
            )
        }

        analysisJob?.cancel()
        val job = scope.launch(start = CoroutineStart.LAZY) {
            val thisJob = coroutineContext[Job]
            val pipeline = pipelineFactory(image)
            try {
                val finalStage = pipeline.execute(
                    initialDiagnostics = initialDiagnostics,
                    onDiagnostics = { diag ->
                        if (analysisJob === thisJob) {
                            _state.update { state ->
                                state.copy(
                                    diagnostics = diag.copy(
                                        appCheckLabel = appCheck.labelAr,
                                        appCheckDetail = appCheck.detailAr,
                                    ),
                                    nowMillis = nowMillisProvider(),
                                )
                            }
                        }
                    },
                    onStage = { stage ->
                        if (analysisJob === thisJob) {
                            _state.update {
                                it.copy(
                                    stage = stage,
                                    nowMillis = nowMillisProvider(),
                                )
                            }
                        }
                    },
                )
                if (finalStage is AnalysisStage.Success && analysisJob === thisJob) {
                    onSuccess?.invoke()
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

    fun cancelAnalysis() {
        val current = _state.value
        if (!current.isBusy) return
        analysisJob?.cancel()
        analysisJob = null
        val now = nowMillisProvider()
        val elapsed = current.diagnostics.aiStartedAtMillis?.let { (now - it).coerceAtLeast(0L) }
        _state.update { state ->
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

    fun resetSelection(): Boolean {
        if (_state.value.isBusy) return false
        _state.update {
            it.copy(
                selectedImage = null,
                stage = AnalysisStage.Idle,
                diagnostics = CaptureDiagnostics(),
                nowMillis = nowMillisProvider(),
            )
        }
        return true
    }

    fun consumeSuccess() {
        _state.update {
            it.copy(
                selectedImage = null,
                stage = AnalysisStage.Idle,
                diagnostics = CaptureDiagnostics(),
                nowMillis = nowMillisProvider(),
            )
        }
    }

    fun clear() {
        analysisJob?.cancel()
        analysisJob = null
    }
}
