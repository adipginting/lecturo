package com.adipginting.lecturo.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

/** A chat image read back for an API payload: base64 JPEG plus its MIME type. */
data class ChatImage(val base64: String, val mime: String)

/**
 * Long-edge cap from Anthropic's vision guidance; bigger images cost tokens
 * without adding legible detail, so everything is scaled down to this on save.
 */
internal const val CHAT_IMAGE_MAX_LONG_EDGE = 1568

internal const val CHAT_IMAGE_JPEG_QUALITY = 85

/** Target pixel size with the long edge capped; identity when already small enough. */
internal fun scaledImageSize(
    width: Int,
    height: Int,
    maxLongEdge: Int = CHAT_IMAGE_MAX_LONG_EDGE,
): Pair<Int, Int> {
    require(width > 0 && height > 0) { "Invalid image size ${width}x$height" }
    val longEdge = maxOf(width, height)
    if (longEdge <= maxLongEdge) return width to height
    val scale = maxLongEdge.toFloat() / longEdge
    return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
}

/**
 * Image files for chat, under `filesDir/chat_images/` like the covers cache.
 * The database only holds paths; bytes are read back at send time so no blobs
 * live in Room.
 */
class ChatImages(private val dir: File) {

    /** Saves [bitmap] downscaled and JPEG-encoded; returns the file's path. */
    suspend fun save(bitmap: Bitmap): String = withContext(Dispatchers.IO) {
        dir.mkdirs()
        val (width, height) = scaledImageSize(bitmap.width, bitmap.height)
        val scaled = if (width == bitmap.width && height == bitmap.height) bitmap
        else Bitmap.createScaledBitmap(bitmap, width, height, true)
        val file = File(dir, "${UUID.randomUUID()}.jpg")
        file.outputStream().use {
            scaled.compress(Bitmap.CompressFormat.JPEG, CHAT_IMAGE_JPEG_QUALITY, it)
        }
        file.absolutePath
    }

    /** Saves raw image bytes (any format BitmapFactory reads); returns the path. */
    suspend fun save(bytes: ByteArray): String? {
        val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: return null
        return save(bitmap)
    }

    /** Reads a saved image back for a payload; null when the file is gone. */
    suspend fun load(path: String): ChatImage? = withContext(Dispatchers.IO) {
        val file = File(path)
        if (!file.isFile) null
        else ChatImage(Base64.encodeToString(file.readBytes(), Base64.NO_WRAP), "image/jpeg")
    }

    suspend fun delete(path: String) {
        withContext(Dispatchers.IO) { File(path).delete() }
    }

    suspend fun delete(paths: List<String>) {
        withContext(Dispatchers.IO) { paths.forEach { File(it).delete() } }
    }
}
