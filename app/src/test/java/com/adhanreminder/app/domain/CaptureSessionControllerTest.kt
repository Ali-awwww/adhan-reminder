package com.adhanreminder.app.domain

import com.adhanreminder.app.data.AppCheckDiagnostics
import com.adhanreminder.app.data.AttemptDiagnostic
import com.adhanreminder.app.data.AttemptMode
import com.adhanreminder.app.data.GeminiClient
import com.adhanreminder.app.data.ImagePreparationResult
import com.adhanreminder.app.data.ModelConfig
import com.adhanreminder.app.data.StreamAttemptRunner
import com.adhanreminder.app.data.TimetableResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.util.concurrent.atomic.AtomicInteger

class CaptureSessionControllerTest {
    private val sampleJson = """{"hijriMonthName":"محرم","hijriYear":1447,"fajrNoteMinutes":20,"days":[{"gregorianDate":"2026-11-16","hijriDay":1,"fajr":{"time":"05:00","period":"AM"},"dhuhr":{"time":"12:00","period":"PM"},"asr":{"time":"15:00","period":null},"maghrib":{"time":"17:30","period":null},"isha":{"time":"19:00","period":null}}],"flags":[]}"""

    @Test fun selectImageAutomaticallyStartsGeminiAnalysisAndCompletes() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val extractCalls = AtomicInteger(0)
            val savedDone = CompletableDeferred<Unit>()
            val controller = CaptureSessionController<String>(
                scope = scope,
                appCheckProvider = {
                    AppCheckDiagnostics(initialized = true, labelAr = "مفعّل · Debug (Token موجود)")
                },
                pipelineFactory = { imageRef ->
                    CapturePipeline(
                        prepareImage = {
                            assertEquals("content://sample/timetable.jpg", imageRef)
                            ImagePreparationResult.Success(ByteArray(1024) { 1 })
                        },
                        getQuotaRecord = { ScanQuotaRecord() },
                        recordScanAttempt = { _, _ -> },
                        extractTimetable = { _, _ ->
                            extractCalls.incrementAndGet()
                            TimetableResult.Success(sampleJson, ModelConfig.MODEL_NAME, AttemptMode.SCHEMA)
                        },
                        saveValidatedSchedule = { _, validated ->
                            PersistedDraftResult(77L, validated.days.size)
                        },
                    )
                },
            )

            val accepted = controller.selectImage("content://sample/timetable.jpg") {
                savedDone.complete(Unit)
            }
            assertTrue(accepted)
            withTimeout(5_000L) { savedDone.await() }
            withTimeout(5_000L) {
                controller.state.first { it.stage is AnalysisStage.Success }
            }

            val finalState = controller.state.value
            assertEquals(1, extractCalls.get())
            assertEquals(AnalysisStage.Success(77L, 1), finalState.stage)
            assertEquals("مفعّل · Debug (Token موجود)", finalState.diagnostics.appCheckLabel)

            controller.consumeSuccess()
            assertNull(controller.state.value.selectedImage)
            assertEquals(AnalysisStage.Idle, controller.state.value.stage)
        } finally {
            scope.cancel()
        }
    }

    @Test fun ignoresNewSelectionWhileBusyAndSupportsCancelThenManualRetry() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val aiStarted = CompletableDeferred<Unit>()
            val holdFirstAttempt = CompletableDeferred<Unit>()
            val extractCalls = AtomicInteger(0)
            val retryDone = CompletableDeferred<Unit>()
            var clock = 10_000L

            val controller = CaptureSessionController<String>(
                scope = scope,
                nowMillisProvider = { clock },
                pipelineFactory = {
                    CapturePipeline(
                        prepareImage = { ImagePreparationResult.Success(ByteArray(1024) { 2 }) },
                        getQuotaRecord = { ScanQuotaRecord() },
                        recordScanAttempt = { _, _ -> },
                        extractTimetable = { _, _ ->
                            val attempt = extractCalls.incrementAndGet()
                            if (attempt == 1) {
                                aiStarted.complete(Unit)
                                holdFirstAttempt.await()
                            }
                            TimetableResult.Success(sampleJson, ModelConfig.MODEL_NAME, AttemptMode.SCHEMA)
                        },
                        saveValidatedSchedule = { _, validated ->
                            PersistedDraftResult(88L, validated.days.size)
                        },
                        nowMillisProvider = { clock },
                    )
                },
            )

            assertTrue(controller.selectImage("camera://photo1.jpg"))
            withTimeout(5_000L) { aiStarted.await() }
            withTimeout(5_000L) {
                controller.state.first { it.stage is AnalysisStage.AnalyzingWithAi }
            }

            assertFalse(controller.selectImage("gallery://photo2.jpg"))
            assertEquals("camera://photo1.jpg", controller.state.value.selectedImage)

            clock = 14_000L
            controller.cancelAnalysis()
            val cancelledState = controller.state.value
            assertEquals(AnalysisStage.PreviewReady, cancelledState.stage)
            assertTrue(cancelledState.diagnostics.isCancelled)
            assertEquals(4_000L, cancelledState.diagnostics.latencyMillis)
            assertEquals("camera://photo1.jpg", cancelledState.selectedImage)

            controller.startAnalysis { retryDone.complete(Unit) }
            withTimeout(5_000L) { retryDone.await() }
            withTimeout(5_000L) {
                controller.state.first { it.stage is AnalysisStage.Success }
            }
            assertEquals(2, extractCalls.get())
            assertEquals(AnalysisStage.Success(88L, 1), controller.state.value.stage)
        } finally {
            scope.cancel()
        }
    }

    @Test fun quotaBlockOnAutoSelectSurfacesErrorWithoutCallingGemini() = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val today = LocalDate.of(2026, 11, 17)
            val now = 50_000L
            var geminiCalled = false
            val exhaustedRecord = ScanQuotaRecord(
                dayKey = ScanQuotaPolicy.dayKey(today),
                dailyCount = ScanQuotaPolicy.MAX_DAILY_SCANS,
                monthKey = ScanQuotaPolicy.monthKey(today),
                monthlyCount = ScanQuotaPolicy.MAX_DAILY_SCANS,
                lastAttemptMillis = now - 30_000L,
            )

            val controller = CaptureSessionController<String>(
                scope = scope,
                nowMillisProvider = { now },
                pipelineFactory = {
                    CapturePipeline(
                        prepareImage = { ImagePreparationResult.Success(ByteArray(512) { 1 }) },
                        getQuotaRecord = { exhaustedRecord },
                        recordScanAttempt = { _, _ -> error("quota should not be recorded") },
                        extractTimetable = { _, _ ->
                            geminiCalled = true
                            error("Gemini should not be called when quota is exhausted")
                        },
                        saveValidatedSchedule = { _, _ -> error("should not persist") },
                        nowMillisProvider = { now },
                        todayProvider = { today },
                    )
                },
            )

            assertTrue(controller.selectImage("gallery://timetable.jpg"))
            val state = withTimeout(5_000L) {
                controller.state.first { it.stage is AnalysisStage.Error }
            }
            val errorStage = state.stage as AnalysisStage.Error
            assertFalse(geminiCalled)
            assertTrue(errorStage.canRetryWithSameImage)
            assertTrue(errorStage.message.isNotBlank())
        } finally {
            scope.cancel()
        }
    }

    @Test fun appCheckFailureStopsImmediatelyWithoutFallbackOrQuotaDeduction() = runBlocking {
        val clientCalls = AtomicInteger(0)
        val client = GeminiClient(
            streamRunner = StreamAttemptRunner { _, _, _, _ ->
                clientCalls.incrementAndGet()
                throw IllegalStateException("App Check failed: HTTP 403 PERMISSION_DENIED")
            },
        )
        val result = client.extractTimetable(byteArrayOf(1, 2, 3))
        assertTrue(result is TimetableResult.Unexpected)
        assertTrue(result.diagnosticDetail?.contains("403") == true)

        var recorded = false
        val pipeline = CapturePipeline(
            prepareImage = { ImagePreparationResult.Success(byteArrayOf(1, 2, 3)) },
            getQuotaRecord = { ScanQuotaRecord() },
            recordScanAttempt = { _, _ -> recorded = true },
            extractTimetable = { _, _ ->
                TimetableResult.Unexpected(
                    userFriendlyDetail = "تعذّر التحقق من صلاحية التطبيق (App Check).",
                    diagnosticDetail = "FirebaseAIException: HTTP 403 App Check token invalid",
                    isAppCheckFailure = true,
                    attempts = listOf(AttemptDiagnostic(ModelConfig.MODEL_NAME, AttemptMode.SCHEMA, 100L, false, "HTTP 403")),
                )
            },
            saveValidatedSchedule = { _, _ -> error("should not save") },
        )
        val stage = pipeline.execute { }
        assertFalse(recorded)
        assertTrue(stage is AnalysisStage.Error)
    }
}
