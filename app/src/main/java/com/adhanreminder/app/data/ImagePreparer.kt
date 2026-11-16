package com.adhanreminder.app.data

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream

sealed class ImagePreparationResult {
    data class Success(val jpeg: ByteArray) : ImagePreparationResult()
    data object Failure : ImagePreparationResult()
}

suspend fun prepareForUpload(contentResolver: ContentResolver, uri: Uri): ImagePreparationResult = withContext(Dispatchers.IO) {
    var decoded: Bitmap? = null
    try {
        tryPassThroughSmallJpeg(contentResolver, uri)?.let { rawJpeg ->
            return@withContext ImagePreparationResult.Success(rawJpeg)
        }

        val source = ImageDecoder.createSource(contentResolver, uri)
        decoded = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > MAX_DIMENSION_PX) {
                val scale = MAX_DIMENSION_PX.toFloat() / longest
                decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
            }
            decoder.isMutableRequired = false
        }
        fun compress(quality: Int): ByteArray = ByteArrayOutputStream().use { out ->
            decoded!!.compress(Bitmap.CompressFormat.JPEG, quality, out)
            out.toByteArray()
        }
        var bytes = compress(90)
        if (bytes.size > MAX_UPLOAD_BYTES) bytes = compress(70)
        if (bytes.size > MAX_UPLOAD_BYTES) return@withContext ImagePreparationResult.Failure
        ImagePreparationResult.Success(bytes)
    } catch (_: Exception) {
        ImagePreparationResult.Failure
    } finally {
        decoded?.recycle()
    }
}

private fun tryPassThroughSmallJpeg(contentResolver: ContentResolver, uri: Uri): ByteArray? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, opts)
    } ?: return null

    if (opts.outMimeType != "image/jpeg") return null
    val longest = maxOf(opts.outWidth, opts.outHeight)
    if (longest !in 1..MAX_DIMENSION_PX) return null

    val raw = contentResolver.openInputStream(uri)?.use { readBounded(it, PASS_THROUGH_MAX_BYTES) } ?: return null
    return raw.takeIf { it.isNotEmpty() }
}

private fun readBounded(input: InputStream, maxBytes: Int): ByteArray? {
    val out = ByteArrayOutputStream()
    val buffer = ByteArray(8192)
    var total = 0
    while (true) {
        val read = input.read(buffer)
        if (read == -1) break
        total += read
        if (total > maxBytes) return null
        out.write(buffer, 0, read)
    }
    return out.toByteArray()
}

private const val MAX_DIMENSION_PX = 2048
private const val PASS_THROUGH_MAX_BYTES = 1024 * 1024
private const val MAX_UPLOAD_BYTES = 4 * 1024 * 1024
