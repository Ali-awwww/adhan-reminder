package com.adhanreminder.app.data

import android.graphics.Bitmap
import com.adhanreminder.app.domain.ExtractionParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class GeminiOcrServiceTest {
    private val sampleJson = """{"hijriMonthName":"محرم","hijriYear":1447,"fajrNoteMinutes":20,"days":[{"gregorianDate":"2026-11-16","hijriDay":1,"fajr":{"time":"05:00","period":"AM"},"dhuhr":{"time":"12:00","period":"PM"},"asr":{"time":"15:00","period":null},"maghrib":{"time":"17:30","period":null},"isha":{"time":"19:00","period":null}}],"flags":[]}"""

    private fun stubBitmap(): Bitmap {
        val unsafeClass = Class.forName("sun.misc.Unsafe")
        val field = unsafeClass.getDeclaredField("theUnsafe").apply { isAccessible = true }
        val unsafe = field.get(null)
        val allocateInstance = unsafeClass.getMethod("allocateInstance", Class::class.java)
        return allocateInstance.invoke(unsafe, Bitmap::class.java) as Bitmap
    }

    @Test fun extractTimetableEncodesBitmapAndParsesStructuredResponse() = runBlocking {
        val client = GeminiClient(
            streamRunner = StreamAttemptRunner { modelName, mode, _, _ ->
                assertEquals(ModelConfig.MODEL_NAME, modelName)
                assertEquals(AttemptMode.SCHEMA, mode)
                sampleJson
            },
        )
        val service = GeminiOcrService(
            geminiClient = client,
            bitmapEncoder = BitmapToJpegEncoder { ImagePreparationResult.Success(byteArrayOf(10, 20, 30)) },
        )

        val result = service.extractTimetable(stubBitmap())
        assertTrue(result is TimetableResult.Success)
        val parsed = ExtractionParser.parse((result as TimetableResult.Success).rawJson)
        assertTrue(parsed.isSuccess)
    }

    @Test fun extractTimetableReturnsUnexpectedWhenBitmapPreparationFails() = runBlocking {
        val client = GeminiClient(
            streamRunner = StreamAttemptRunner { _, _, _, _ -> error("should not invoke Gemini") },
        )
        val service = GeminiOcrService(
            geminiClient = client,
            bitmapEncoder = BitmapToJpegEncoder { ImagePreparationResult.Failure },
        )

        val result = service.extractTimetable(stubBitmap())
        assertTrue(result is TimetableResult.Unexpected)
    }

    @Test fun extractOcrTextReturnsTrimmedTextAndHandlesBlankNetworkAndTimeoutErrors() = runBlocking {
        val successService = GeminiOcrService(
            bitmapRunner = BitmapOcrRunner { modelName, timeoutMs, _, prompt ->
                assertEquals(ModelConfig.MODEL_NAME, modelName)
                assertEquals(GeminiClient.REQUEST_TIMEOUT_MS, timeoutMs)
                assertEquals(GeminiOcrService.DEFAULT_OCR_PROMPT, prompt)
                "  Fajr 05:00  "
            },
        )
        val success = successService.extractOcrText(stubBitmap())
        assertEquals(OcrTextResult.Success("Fajr 05:00", ModelConfig.MODEL_NAME), success)

        val blankService = GeminiOcrService(bitmapRunner = BitmapOcrRunner { _, _, _, _ -> "   " })
        assertTrue(blankService.extractOcrText(stubBitmap()) is OcrTextResult.Error)

        val networkService = GeminiOcrService(bitmapRunner = BitmapOcrRunner { _, _, _, _ -> throw IOException("offline") })
        val networkErr = networkService.extractOcrText(stubBitmap()) as OcrTextResult.Error
        assertTrue(networkErr.diagnosticDetail?.contains("IOException") == true)

        val timeoutService = GeminiOcrService(
            bitmapRunner = BitmapOcrRunner { _, _, _, _ ->
                // Trigger an immediate TimeoutCancellationException via nested withTimeout(1)
                kotlinx.coroutines.withTimeout(1L) {
                    delay(1_000L)
                    ""
                }
            },
        )
        val timeoutErr = timeoutService.extractOcrText(stubBitmap()) as OcrTextResult.Error
        assertTrue(timeoutErr.diagnosticDetail?.contains("TimeoutCancellationException") == true)
    }

    @Test fun extractOcrTextRethrowsCancellationException() {
        val service = GeminiOcrService(
            bitmapRunner = BitmapOcrRunner { _, _, _, _ -> throw CancellationException("cancelled") },
        )
        var rethrown = false
        try {
            runBlocking { service.extractOcrText(stubBitmap()) }
        } catch (_: CancellationException) {
            rethrown = true
        }
        assertTrue(rethrown)
    }
}
