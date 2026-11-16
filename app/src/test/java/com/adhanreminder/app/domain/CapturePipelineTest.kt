package com.adhanreminder.app.domain

import com.adhanreminder.app.data.AttemptDiagnostic
import com.adhanreminder.app.data.AttemptMode
import com.adhanreminder.app.data.AttemptProgress
import com.adhanreminder.app.data.GeminiClient
import com.adhanreminder.app.data.ImagePreparationResult
import com.adhanreminder.app.data.ModelConfig
import com.adhanreminder.app.data.StreamAttemptRunner
import com.adhanreminder.app.data.TimetableResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.LocalDate

class CapturePipelineTest {
    private val sampleJson = """{"hijriMonthName":"محرم","hijriYear":1447,"fajrNoteMinutes":20,"days":[{"gregorianDate":"2026-11-16","hijriDay":1,"fajr":{"time":"05:00","period":"AM"},"dhuhr":{"time":"12:00","period":"PM"},"asr":{"time":"15:00","period":null},"maghrib":{"time":"17:30","period":null},"isha":{"time":"19:00","period":null}}],"flags":[]}"""

    @Test fun emitsOrderedStagesAndDiagnosticsThroughFallbackSuccess() = runBlocking {
        val stages = mutableListOf<AnalysisStage>()
        val diagnosticsHistory = mutableListOf<CaptureDiagnostics>()
        var clock = 10_000L
        var recorded = false
        val simulatedAttempts = listOf(
            AttemptDiagnostic(ModelConfig.MODEL_NAME, AttemptMode.SCHEMA, 1_500L, false, "TimeoutCancellationException after 1500ms"),
            AttemptDiagnostic(ModelConfig.FALLBACK_MODEL_NAME, AttemptMode.SCHEMA, 1_000L, true, "استلم ${sampleJson.length} حرفاً"),
        )
        val pipeline = CapturePipeline(
            prepareImage = { ImagePreparationResult.Success(ByteArray(2048) { 1 }) },
            getQuotaRecord = { ScanQuotaRecord() },
            recordScanAttempt = { _, _ -> recorded = true },
            extractTimetable = { _, onProgress ->
                onProgress(AttemptProgress(ModelConfig.MODEL_NAME, AttemptMode.SCHEMA, false, null, emptyList()))
                clock += 1_500L
                onProgress(
                    AttemptProgress(
                        activeModelName = ModelConfig.FALLBACK_MODEL_NAME,
                        activeMode = AttemptMode.SCHEMA,
                        isFallback = true,
                        reasonAr = "ضغط على ${ModelConfig.MODEL_NAME} — جارٍ التحويل التلقائي",
                        completedAttempts = simulatedAttempts.take(1),
                    ),
                )
                clock += 1_000L
                TimetableResult.Success(sampleJson, ModelConfig.FALLBACK_MODEL_NAME, AttemptMode.SCHEMA, simulatedAttempts)
            },
            saveValidatedSchedule = { _, validated -> PersistedDraftResult(42L, validated.days.size) },
            nowMillisProvider = { clock },
            todayProvider = { LocalDate.of(2026, 11, 16) },
        )

        val result = pipeline.execute(
            initialDiagnostics = CaptureDiagnostics(appCheckLabel = "مفعّل · Debug"),
            onDiagnostics = { diagnosticsHistory += it },
            onStage = { stages += it },
        )

        assertEquals(AnalysisStage.Success(42L, 1), result)
        assertTrue(recorded)
        assertEquals(
            listOf(
                AnalysisStage.PreparingImage,
                AnalysisStage.AnalyzingWithAi,
                AnalysisStage.ValidatingSchedule,
                AnalysisStage.Success(42L, 1),
            ),
            stages,
        )
        val latest = diagnosticsHistory.last()
        assertEquals("مفعّل · Debug", latest.appCheckLabel)
        assertEquals(ModelConfig.FALLBACK_MODEL_NAME, latest.activeModelName)
        assertTrue(latest.usedFallbackModel)
        assertEquals(2048, latest.imageBytes)
        assertEquals(2_500L, latest.latencyMillis)
        assertEquals(sampleJson.length, latest.responseChars)
        assertEquals(2, latest.attempts.size)
    }

    @Test fun serverAndNetworkErrorsDoNotConsumeQuotaAndPreserveVerbatimDetail() = runBlocking {
        val failures = listOf(
            TimetableResult.ServiceUnavailable(
                rawError = "FirebaseAIException: HTTP 503 high demand on fallback",
                attempts = listOf(AttemptDiagnostic(ModelConfig.MODEL_NAME, AttemptMode.SCHEMA, 1_200L, false, "HTTP 503")),
            ),
            TimetableResult.Timeout(rawError = "TimeoutCancellationException after 60000ms"),
            TimetableResult.NoNetwork(rawError = "UnknownHostException: Unable to resolve host"),
        )
        failures.forEach { failureResult ->
            var recorded = false
            var lastDiag: CaptureDiagnostics? = null
            var clock = 5_000L
            val pipeline = CapturePipeline(
                prepareImage = { ImagePreparationResult.Success(byteArrayOf(1, 2, 3)) },
                getQuotaRecord = { ScanQuotaRecord() },
                recordScanAttempt = { _, _ -> recorded = true },
                extractTimetable = { _, _ ->
                    clock += 1_200L
                    failureResult
                },
                saveValidatedSchedule = { _, _ -> error("should not persist") },
                nowMillisProvider = { clock },
            )

            val result = pipeline.execute(
                onDiagnostics = { lastDiag = it },
                onStage = { },
            )

            assertFalse("Quota should not be consumed for $failureResult", recorded)
            assertTrue(result is AnalysisStage.Error)
            assertTrue((result as AnalysisStage.Error).canRetryWithSameImage)
            assertNotNull(lastDiag?.statusDetail)
            assertEquals(1_200L, lastDiag?.latencyMillis)
            assertEquals(failureResult.diagnosticDetail, lastDiag?.statusDetail)
        }
    }

    @Test fun imagePrepFailureStopsWithoutConsumingQuota() = runBlocking {
        var recorded = false
        val pipeline = CapturePipeline(
            prepareImage = { ImagePreparationResult.Failure },
            getQuotaRecord = { ScanQuotaRecord() },
            recordScanAttempt = { _, _ -> recorded = true },
            extractTimetable = { _, _ -> error("should not call Gemini") },
            saveValidatedSchedule = { _, _ -> error("should not persist") },
        )

        val result = pipeline.execute { }

        assertFalse(recorded)
        assertTrue(result is AnalysisStage.Error)
        assertFalse((result as AnalysisStage.Error).canRetryWithSameImage)
    }

    @Test fun cancellationExceptionIsRethrown() {
        val pipeline = CapturePipeline(
            prepareImage = { ImagePreparationResult.Success(byteArrayOf(1)) },
            getQuotaRecord = { ScanQuotaRecord() },
            recordScanAttempt = { _, _ -> },
            extractTimetable = { _, _ -> throw CancellationException("cancelled") },
            saveValidatedSchedule = { _, _ -> error("should not persist") },
        )

        var caught = false
        try {
            runBlocking { pipeline.execute { } }
        } catch (_: CancellationException) {
            caught = true
        }
        assertTrue(caught)
    }

    @Test fun parseFailureRecordsResponsePreviewWithoutConsumingQuota() = runBlocking {
        var recorded = false
        var lastDiag: CaptureDiagnostics? = null
        val partialJson = """{"hijriMonthName":"محرم"}"""
        val pipeline = CapturePipeline(
            prepareImage = { ImagePreparationResult.Success(ByteArray(1024) { 1 }) },
            getQuotaRecord = { ScanQuotaRecord() },
            recordScanAttempt = { _, _ -> recorded = true },
            extractTimetable = { _, _ ->
                TimetableResult.Success(partialJson, ModelConfig.FALLBACK_MODEL_NAME, AttemptMode.PLAIN_JSON)
            },
            saveValidatedSchedule = { _, _ -> error("should not persist") },
        )

        val result = pipeline.execute(
            onDiagnostics = { lastDiag = it },
            onStage = { },
        )

        assertFalse(recorded)
        assertTrue(result is AnalysisStage.Error)
        assertEquals(partialJson, lastDiag?.responsePreview)
        assertTrue(lastDiag?.statusDetail?.contains("days") == true)
    }

    @Test fun geminiClientFallsBackToPlainJsonWhenSchemaAttemptsFail() = runBlocking {
        val client = GeminiClient(
            streamRunner = StreamAttemptRunner { modelName, mode, _, _ ->
                when {
                    modelName == ModelConfig.MODEL_NAME && mode == AttemptMode.SCHEMA ->
                        throw IllegalStateException("This model is currently experiencing high demand (503)")
                    modelName == ModelConfig.FALLBACK_MODEL_NAME && mode == AttemptMode.SCHEMA ->
                        """{"hijriMonthName":"محرم"}""" // Missing days field triggers PLAIN_JSON fallback
                    modelName == ModelConfig.FALLBACK_MODEL_NAME && mode == AttemptMode.PLAIN_JSON ->
                        "```json\n$sampleJson\n```"
                    else -> error("Unexpected call $modelName / $mode")
                }
            },
        )

        val progressEvents = mutableListOf<AttemptProgress>()
        val result = client.extractTimetable(byteArrayOf(1, 2, 3)) { progressEvents += it }

        assertTrue(result is TimetableResult.Success)
        val success = result as TimetableResult.Success
        assertEquals(ModelConfig.FALLBACK_MODEL_NAME, success.modelUsed)
        assertEquals(AttemptMode.PLAIN_JSON, success.modeUsed)
        assertEquals(3, success.attempts.size)
        assertTrue(success.attempts[0].rawDetail.contains("high demand (503)"))
        assertFalse(success.attempts[1].succeeded)
        assertTrue(success.attempts[2].succeeded)
        assertTrue(ExtractionParser.parse(success.rawJson).isSuccess)
    }

    @Test fun geminiClientPreservesVerbatimErrorWhenAllAttemptsFail() = runBlocking {
        val exactServerMessage = "Internal Server Error 500: backend model shard unavailable"
        val client = GeminiClient(
            streamRunner = StreamAttemptRunner { modelName, mode, _, _ ->
                when {
                    modelName == ModelConfig.MODEL_NAME ->
                        throw IllegalStateException("Spikes in demand on primary")
                    mode == AttemptMode.SCHEMA ->
                        throw IllegalStateException("Schema execution error 500")
                    else ->
                        throw IllegalStateException(exactServerMessage)
                }
            },
        )

        val result = client.extractTimetable(byteArrayOf(1, 2, 3))

        assertTrue(result is TimetableResult.ServiceUnavailable)
        assertTrue(result.diagnosticDetail?.contains(exactServerMessage) == true)
        assertEquals(3, result.attempts.size)
        assertTrue(result.attempts[0].rawDetail.contains("Spikes in demand on primary"))
        assertTrue(result.attempts[1].rawDetail.contains("Schema execution error 500"))
        assertTrue(result.attempts[2].rawDetail.contains(exactServerMessage))
    }

    @Test fun probeServerChecksBothModelsWithoutThrowing() = runBlocking {
        val client = GeminiClient(
            streamRunner = StreamAttemptRunner { modelName, mode, _, _ ->
                assertEquals(AttemptMode.TEXT_PING, mode)
                if (modelName == ModelConfig.MODEL_NAME) {
                    throw IOException("Connection reset by peer")
                } else {
                    "OK"
                }
            },
        )

        val report = client.probeServer()

        assertEquals(2, report.attempts.size)
        assertFalse(report.attempts[0].succeeded)
        assertTrue(report.attempts[0].rawDetail.contains("Connection reset by peer"))
        assertTrue(report.attempts[1].succeeded)
        assertTrue(report.anySucceeded)
    }

    @Test fun geminiClientHas60sTimeoutAndLiteFallback() {
        assertEquals(60_000L, GeminiClient.REQUEST_TIMEOUT_MS)
        assertEquals(15_000L, GeminiClient.PROBE_TIMEOUT_MS)
        assertEquals("gemini-3.5-flash-lite", ModelConfig.FALLBACK_MODEL_NAME)
        assertTrue(TimetableResult.Timeout().userMessage.contains("60"))
    }
}
