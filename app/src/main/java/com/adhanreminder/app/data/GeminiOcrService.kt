package com.adhanreminder.app.data

import android.graphics.Bitmap
import android.util.Log
import com.google.firebase.Firebase
import com.google.firebase.ai.GenerativeModel
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.FirebaseAIException
import com.google.firebase.ai.type.content
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.withTimeout
import java.io.IOException

sealed interface OcrTextResult {
    data class Success(
        val text: String,
        val modelUsed: String = ModelConfig.MODEL_NAME,
    ) : OcrTextResult

    data class Error(
        val message: String,
        val diagnosticDetail: String? = null,
        val isAppCheckFailure: Boolean = false,
    ) : OcrTextResult
}

internal fun interface BitmapOcrRunner {
    suspend fun generate(modelName: String, timeoutMs: Long, bitmap: Bitmap, prompt: String): String
}

internal fun interface BitmapToJpegEncoder {
    suspend fun encode(bitmap: Bitmap): ImagePreparationResult
}

/**
 * Service class that lazily initializes the Firebase AI Logic (Gemini) client
 * and provides Bitmap-based OCR extraction methods.
 */
class GeminiOcrService internal constructor(
    private val geminiClient: GeminiClient = GeminiClient(),
    private val bitmapEncoder: BitmapToJpegEncoder = BitmapToJpegEncoder { prepareBitmapForUpload(it) },
    private val bitmapRunner: BitmapOcrRunner? = null,
) {
    constructor(geminiClient: GeminiClient = GeminiClient()) : this(
        geminiClient = geminiClient,
        bitmapEncoder = BitmapToJpegEncoder { prepareBitmapForUpload(it) },
        bitmapRunner = null,
    )

    // Lazily initialize Firebase AI Logic GenerativeModel so App Check is configured first.
    private val ocrModel: GenerativeModel by lazy {
        Firebase.ai.generativeModel(
            modelName = ModelConfig.MODEL_NAME,
            generationConfig = generationConfig {
                temperature = 0f
            },
        )
    }

    /**
     * Sends a [Bitmap] image to Gemini via Firebase AI Logic for structured
     * prayer timetable OCR extraction.
     */
    suspend fun extractTimetable(
        bitmap: Bitmap,
        onProgress: (AttemptProgress) -> Unit = {},
    ): TimetableResult {
        val prepared = bitmapEncoder.encode(bitmap)
        if (prepared !is ImagePreparationResult.Success) {
            return TimetableResult.Unexpected(
                userFriendlyDetail = "تعذّر تجهيز الصورة للتحليل. تأكد من صلاحية الصورة وحجمها.",
                diagnosticDetail = "Bitmap preparation failed (recycled, empty, or exceeded 4 MB)",
            )
        }
        return geminiClient.extractTimetable(prepared.jpeg, onProgress)
    }

    /**
     * Sends a [Bitmap] image directly using `content { image(bitmap); text(prompt) }`
     * to extract raw OCR text with a 60-second timeout and transparent error propagation.
     */
    suspend fun extractOcrText(
        bitmap: Bitmap,
        prompt: String = DEFAULT_OCR_PROMPT,
    ): OcrTextResult {
        if (isBitmapInvalid(bitmap)) {
            return OcrTextResult.Error(
                message = "الصورة غير صالحة للقراءة.",
                diagnosticDetail = "Bitmap is recycled or has non-positive dimensions",
            )
        }

        return try {
            val responseText = if (bitmapRunner != null) {
                withTimeout(GeminiClient.REQUEST_TIMEOUT_MS) {
                    bitmapRunner.generate(ModelConfig.MODEL_NAME, GeminiClient.REQUEST_TIMEOUT_MS, bitmap, prompt)
                }
            } else {
                val preparedBitmap = scaleDownIfNeeded(bitmap)
                try {
                    withTimeout(GeminiClient.REQUEST_TIMEOUT_MS) {
                        val response = ocrModel.generateContent(
                            content {
                                image(preparedBitmap)
                                text(prompt)
                            },
                        )
                        response.text.orEmpty()
                    }
                } finally {
                    if (preparedBitmap !== bitmap && !preparedBitmap.isRecycled) {
                        preparedBitmap.recycle()
                    }
                }
            }

            if (responseText.isBlank()) {
                OcrTextResult.Error(
                    message = "لم يُرجع النموذج نصاً من الصورة.",
                    diagnosticDetail = "Empty OCR response from ${ModelConfig.MODEL_NAME}",
                )
            } else {
                OcrTextResult.Success(text = responseText.trim(), modelUsed = ModelConfig.MODEL_NAME)
            }
        } catch (e: TimeoutCancellationException) {
            logError("OCR extraction timed out after ${GeminiClient.REQUEST_TIMEOUT_MS}ms", e)
            OcrTextResult.Error(
                message = "انتهت مهلة قراءة الصورة (60 ثانية). حاول مجدداً.",
                diagnosticDetail = "TimeoutCancellationException after ${GeminiClient.REQUEST_TIMEOUT_MS}ms",
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: FirebaseAIException) {
            logError("Firebase AI OCR extraction failed", e)
            val raw = "${e.javaClass.simpleName}: ${e.localizedMessage.orEmpty()}"
            val lower = raw.lowercase()
            val isAppCheck = "403" in lower || "app check" in lower || "attestation" in lower || "permission_denied" in lower
            OcrTextResult.Error(
                message = if (isAppCheck) {
                    "تعذّر التحقق من صلاحية التطبيق (App Check)."
                } else {
                    e.localizedMessage?.takeIf { it.isNotBlank() } ?: "تعذّرت قراءة النص من الصورة."
                },
                diagnosticDetail = raw.take(280),
                isAppCheckFailure = isAppCheck,
            )
        } catch (e: IOException) {
            logError("Network error during OCR extraction", e)
            OcrTextResult.Error(
                message = "تعذّر الاتصال بالإنترنت أثناء قراءة الصورة.",
                diagnosticDetail = "${e.javaClass.simpleName}: ${e.localizedMessage.orEmpty()}".take(280),
            )
        } catch (e: Exception) {
            logError("Unexpected error during OCR extraction", e)
            OcrTextResult.Error(
                message = e.localizedMessage?.takeIf { it.isNotBlank() } ?: "حدث خطأ أثناء قراءة الصورة.",
                diagnosticDetail = "${e.javaClass.simpleName}: ${e.localizedMessage.orEmpty()}".take(280),
            )
        }
    }

    private fun isBitmapInvalid(bitmap: Bitmap): Boolean = try {
        bitmap.isRecycled || bitmap.width <= 0 || bitmap.height <= 0
    } catch (_: RuntimeException) {
        // Pure JVM unit tests with unmocked Android Bitmap stub.
        false
    }

    private fun scaleDownIfNeeded(bitmap: Bitmap): Bitmap {
        val longest = maxOf(bitmap.width, bitmap.height)
        if (longest <= MAX_BITMAP_DIMENSION_PX) return bitmap
        val scale = MAX_BITMAP_DIMENSION_PX.toFloat() / longest
        val targetW = (bitmap.width * scale).toInt().coerceAtLeast(1)
        val targetH = (bitmap.height * scale).toInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun logError(message: String, throwable: Throwable) {
        try {
            Log.e(TAG, message, throwable)
        } catch (_: RuntimeException) {
            // Unmocked android.util.Log in pure JVM unit tests.
        }
    }

    companion object {
        private const val TAG = "GeminiOcrService"
        private const val MAX_BITMAP_DIMENSION_PX = 2048
        const val DEFAULT_OCR_PROMPT = "Extract all visible text and prayer times accurately from this image."
    }
}
