package com.adipginting.lecturo.chat

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a chat image file decoded to: nothing yet, nothing there, or pixels. */
private sealed interface ChatImageState {
    object Loading : ChatImageState
    object Missing : ChatImageState
    data class Loaded(val bitmap: Bitmap) : ChatImageState
}

/**
 * Largest power-of-two subsample whose result still keeps the long edge at or
 * above [maxPx] — enough detail for the slot, without decoding a screenshot
 * at full size to draw it 40dp wide.
 */
internal fun sampleSizeFor(width: Int, height: Int, maxPx: Int): Int {
    var sample = 1
    while (maxOf(width, height) / (sample * 2) >= maxPx) sample *= 2
    return sample
}

private fun decodeDownsampled(path: String, maxPx: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(path, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    val options = BitmapFactory.Options().apply {
        inSampleSize = sampleSizeFor(bounds.outWidth, bounds.outHeight, maxPx)
    }
    return BitmapFactory.decodeFile(path, options)
}

/**
 * A chat image file, decoded off the main thread. A file that will not decode
 * reads as "Image missing" rather than taking the surface down with it.
 */
@Composable
internal fun ChatImageView(
    path: String,
    modifier: Modifier = Modifier,
    maxPx: Int = 1024,
    contentDescription: String? = null,
) {
    val state by produceState<ChatImageState>(ChatImageState.Loading, path, maxPx) {
        value = withContext(Dispatchers.IO) {
            decodeDownsampled(path, maxPx)
                ?.let { ChatImageState.Loaded(it) }
                ?: ChatImageState.Missing
        }
    }
    when (val current = state) {
        ChatImageState.Loading ->
            Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant))

        ChatImageState.Missing ->
            Box(
                modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "Image missing",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(4.dp),
                )
            }

        is ChatImageState.Loaded ->
            Image(
                bitmap = current.bitmap.asImageBitmap(),
                contentDescription = contentDescription,
                modifier = modifier,
                contentScale = ContentScale.Fit,
            )
    }
}
