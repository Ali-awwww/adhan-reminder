package com.adhanreminder.app.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

sealed class ImagePreparationResult {
    data class Success(val jpeg: ByteArray) : ImagePreparationResult()
    data object Failure : ImagePreparationResult()
}

suspend fun prepareForUpload(contentResolver: ContentResolver, uri: Uri): ImagePreparationResult = withContext(Dispatchers.IO) {
    var decoded: Bitmap? = null
    try {
        val source = ImageDecoder.createSource(contentResolver, uri)
        decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > 2048) {
                val scale = 2048f / longest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            decoder.isMutableRequired = false
        }
        fun compress(quality: Int): ByteArray = ByteArrayOutputStream().use { out ->
            decoded!!.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
        var bytes = compress(85)
        if (bytes.size > MAX_UPLOAD_BYTES) bytes = compress(65)
        if (bytes.size > MAX_UPLOAD_BYTES) return@withContext ImagePreparationResult.Failure
        ImagePreparationResult.Success(bytes)
    } catch (_: Exception) {
        ImagePreparationResult.Failure
    } finally {
        decoded?.recycle()
    }
}

private const val MAX_UPLOAD_BYTES = 4 * 1024 * 1024
