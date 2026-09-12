package com.adipginting.lecturo.library

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.adipginting.lecturo.data.DocumentEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.zip.ZipFile

/**
 * Cover thumbnails for the library rows: extracted from local files, fetched
 * for remote ones, memory-cached either way. Small enough that a fixed list of
 * them costs less than the row text does.
 */
private val coverCache = object : LinkedHashMap<String, Bitmap>(24, 0.75f, true) {
    override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Bitmap>) = size > 60
}

private val coverClient = OkHttpClient()

/**
 * EPUB cover image or first PDF page; null when the file has neither.
 * The first derivation is written to [coversDir] as `<docId>`, so it survives
 * restarts instead of being re-rendered on every launch.
 */
internal suspend fun localCover(
    doc: DocumentEntity,
    documentsDir: File,
    coversDir: File,
): Bitmap? = withContext(Dispatchers.IO) {
    coverCache[doc.id]?.let { return@withContext it }
    val cached = File(coversDir, doc.id)
        .takeIf { it.exists() }
        ?.let { BitmapFactory.decodeFile(it.absolutePath) }
    if (cached != null) {
        synchronized(coverCache) { coverCache[doc.id] = cached }
        return@withContext cached
    }
    val bitmap = runCatching {
        val file = File(documentsDir, doc.fileName)
        if (!file.exists()) return@runCatching null
        when (doc.format) {
            "epub" -> epubCover(file)
            "pdf" -> pdfCover(file)
            else -> null
        }
    }.getOrNull() ?: return@withContext null
    runCatching {
        coversDir.mkdirs()
        File(coversDir, doc.id).outputStream().use {
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, it)
        }
    }
    synchronized(coverCache) { coverCache[doc.id] = bitmap }
    bitmap
}

/**
 * Fetches an offered cover, and returns null for anything that isn't a
 * successful image response — a missing cover is a placeholder, never an error.
 * Byte-level so the contract is testable against a fake server.
 */
internal fun fetchCoverBytes(url: String): ByteArray? = runCatching {
    coverClient.newCall(Request.Builder().url(url).build()).execute().use { response ->
        if (!response.isSuccessful) return@use null
        response.body?.bytes()
    }
}.getOrNull()

/** A cover the remote source offered; null on failure, which shows the placeholder. */
internal suspend fun remoteCover(url: String): Bitmap? = withContext(Dispatchers.IO) {
    coverCache[url]?.let { return@withContext it }
    val bytes = fetchCoverBytes(url) ?: return@withContext null
    val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
        ?: return@withContext null
    synchronized(coverCache) { coverCache[url] = bitmap }
    bitmap
}

/**
 * The EPUB's cover image bytes, or null when it declares none. Pure JVM, so
 * the extraction rules are testable without a device.
 */
internal fun epubCoverBytes(file: File): ByteArray? = runCatching {
    ZipFile(file).use { zip ->
        val container = zip.getEntry("META-INF/container.xml") ?: return@use null
        val containerXml = zip.getInputStream(container).bufferedReader().readText()
        val opfPath = Regex("full-path=\"([^\"]+)\"").find(containerXml)
            ?.groupValues?.get(1) ?: return@use null
        val opf = zip.getEntry(opfPath) ?: return@use null
        val opfXml = zip.getInputStream(opf).bufferedReader().readText()
        val coverId = Regex("<meta[^>]*name=\"cover\"[^>]*content=\"([^\"]+)\"")
            .find(opfXml)?.groupValues?.get(1)
        val href = when (coverId) {
            null -> Regex("<item[^>]*properties=\"[^\"]*cover-image[^\"]*\"[^>]*href=\"([^\"]+)\"")
                .find(opfXml)?.groupValues?.get(1)
            else -> Regex("<item[^>]*id=\"$coverId\"[^>]*href=\"([^\"]+)\"")
                .find(opfXml)?.groupValues?.get(1)
        } ?: return@use null
        val dir = opfPath.substringBeforeLast('/', "")
        val entry = zip.getEntry(if (dir.isEmpty()) href else "$dir/$href") ?: return@use null
        zip.getInputStream(entry).use { it.readBytes() }
    }
}.getOrNull()

private fun epubCover(file: File): Bitmap? =
    epubCoverBytes(file)?.let { BitmapFactory.decodeByteArray(it, 0, it.size) }

private fun pdfCover(file: File): Bitmap? {
    ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
        PdfRenderer(pfd).use { renderer ->
            if (renderer.pageCount == 0) return null
            renderer.openPage(0).use { page ->
                val width = 160
                val height = (width * page.height.toFloat() / page.width).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                return bitmap
            }
        }
    }
}

/** Cover for a local document, loaded off the main thread. */
@Composable
internal fun localCoverBitmap(doc: DocumentEntity): Bitmap? {
    val context = LocalContext.current
    val documentsDir = remember { File(context.filesDir, "documents") }
    val coversDir = remember { File(context.filesDir, "covers") }
    val bitmap by produceState<Bitmap?>(null, doc.id, doc.updatedAt) {
        value = localCover(doc, documentsDir, coversDir)
    }
    return bitmap
}

/** Cover a remote source offered, loaded off the main thread. */
@Composable
internal fun remoteCoverBitmap(url: String?): Bitmap? {
    val bitmap by produceState<Bitmap?>(null, url) {
        value = url?.takeIf { it.isNotBlank() }?.let { remoteCover(it) }
    }
    return bitmap
}

/**
 * The row's thumbnail: a fixed 40x56 frame holding the cover, or the title's
 * first letter when there is no cover to show. Zotero never offers one.
 */
@Composable
internal fun CoverThumb(
    bitmap: Bitmap?,
    title: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(40.dp)
            .height(56.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        val cover = bitmap
        if (cover != null) {
            Image(
                bitmap = cover.asImageBitmap(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            Text(
                text = title.trim().take(1).uppercase(),
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}
