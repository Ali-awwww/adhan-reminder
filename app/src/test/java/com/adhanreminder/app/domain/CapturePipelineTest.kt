package com.adhanreminder.app.domain

import com.adhanreminder.app.data.GeminiClient
import com.adhanreminder.app.data.ImagePreparationResult
import com.adhanreminder.app.data.TimetableResult
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class CapturePipelineTest {
    private val sampleJson = """{"hijriMonthName":"محرم","hijriYear":1447,"fajrNoteMinutes":20,"days":[{"gregorianDate":"2026-11-16","hijriDay":1,"fajr":{"time":"05:00","period":"AM"},"dhuhr":{"time":"12:00","period":"PM"},"asr":{"time":"15:00","period":null},"maghrib":{"time":"17:30","period":null},"isha":{"time":"19:00","period":null}}],"flags":[]}"""

    @Test fun emitsOrderedStagesAndDiagnosticsThroughSuccess() = runBlocking {
        val stages = mutableListOf<AnalysisStage>()
        val diagnosticsHistory = mutableListOf<CaptureDiagnostics>()
        var clock = 10_000L
        var recorded = false
        val pipeline = CapturePipeline(
            prepareImage = { ImagePreparationResult.Success(ByteArray(2048) { 1 }) },
            getQuotaRecord = { ScanQuotaRecord() },
            recordScanAttempt = { _, _ -> recorded = true },
            extractTimetable = {
                clock += 2_500L
                TimetableResult.Success(sampleJson)
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
        assertEquals(2048, latest.imageBytes)
        assertEquals(2_500L, latest.latencyMillis)
        assertEquals(sampleJson.length, latest.responseChars)
    }

    @Test fun networkErrorIsRetryableAndRecordsDiagnosticDetail() = runBlocking {
        val stages = mutableListOf<AnalysisStage>()
        var lastDiag: CaptureDiagnostics? = null
        var clock = 5_000L
        val pipeline = CapturePipeline(
            prepareImage = { ImagePreparationResult.Success(byteArrayOf(1, 2, 3)) },
            getQuotaRecord = { ScanQuotaRecord() },
            recordScanAttempt = { _, _ -> },
            extractTimetable = {
                clock += 1_200L
                TimetableResult.NoNetwork
            },
            saveValidatedSchedule = { _, _ -> error("should not persist") },
            nowMillisProvider = { clock },
        )

        val result = pipeline.execute(
            onDiagnostics = { lastDiag = it },
            onStage = { stages += it },
        )

        assertTrue(result is AnalysisStage.Error)
        assertTrue((result as AnalysisStage.Error).canRetryWithSameImage)
        assertNotNull(lastDiag?.statusDetail)
        assertEquals(1_200L, lastDiag?.latencyMillis)
        assertEquals(TimetableResult.NoNetwork.diagnosticDetail, lastDiag?.statusDetail)
    }

    @Test fun imagePrepFailureStopsWithoutConsumingQuota() = runBlocking {
        var recorded = false
        val pipeline = CapturePipeline(
            prepareImage = { ImagePreparationResult.Failure },
            getQuotaRecord = { ScanQuotaRecord() },
            recordScanAttempt = { _, _ -> recorded = true },
            extractTimetable = { error("should not call Gemini") },
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
            extractTimetable = { throw CancellationException("cancelled") },
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

    @Test fun geminiClientTimeoutIs45Seconds() {
        assertEquals(45_000L, GeminiClient.REQUEST_TIMEOUT_MS)
        assertTrue(TimetableResult.Timeout.userMessage.contains("45"))
    }
}
