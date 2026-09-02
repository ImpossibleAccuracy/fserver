package com.fserver.app.presentation.screens.source.shared.preview.composable

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ThumbnailUtils
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.core.net.toUri
import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.app.presentation.screens.source.shared.preview.model.SourcePreviewUi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File

/**
 * A downsampled preview of one scanned file, or null while there is none.
 *
 * Deliberately small and local: it decodes a tile-sized bitmap on demand and keeps nothing, which
 * is what a grid the user scrolls past once needs. A source whose files are already indexed will
 * want a real cached-preview pipeline instead — this is the stand-in until there is one.
 */
@Composable
fun rememberSourceThumbnail(file: SourcePreviewUi.File): State<ImageBitmap?> {
    val context = LocalContext.current

    if (file.kind != FileKindUi.Image && file.kind != FileKindUi.Video) {
        return remember(file.path) { mutableStateOf(null) }
    }

    return produceState<ImageBitmap?>(initialValue = null, file.locator) {
        value = withContext(Dispatchers.IO) {
            context.decodeThumbnail(file)?.asImageBitmap()
        }
    }
}

private fun Context.decodeThumbnail(file: SourcePreviewUi.File): Bitmap? = runCatching {
    when {
        !file.locator.startsWith('/') -> contentThumbnail(file)
        file.kind == FileKindUi.Video -> videoThumbnail(File(file.locator))
        else -> sampledImage(File(file.locator))
    }
}.onFailure { Timber.v(it, "No thumbnail for %s", file.name) }.getOrNull()

private fun Context.contentThumbnail(file: SourcePreviewUi.File): Bitmap? {
    val uri = file.locator.toUri()

    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        return contentResolver.loadThumbnail(uri, Size(ThumbnailSize, ThumbnailSize), null)
    }

    if (file.kind == FileKindUi.Video) return null

    return contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, Options) }
}

private fun videoThumbnail(file: File): Bitmap? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ThumbnailUtils.createVideoThumbnail(file, Size(ThumbnailSize, ThumbnailSize), null)
    } else {
        @Suppress("DEPRECATION")
        ThumbnailUtils.createVideoThumbnail(file.path, MediaStore.Images.Thumbnails.MINI_KIND)
    }

/**
 * Decoded at roughly tile size rather than in full: a phone gallery holds photos far larger than
 * any tile, and decoding those at full size is what would run the grid out of memory.
 */
private fun sampledImage(file: File): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeFile(file.path, bounds)

    val longest = maxOf(bounds.outWidth, bounds.outHeight)
    if (longest <= 0) return null

    var sample = 1
    while (longest / sample > ThumbnailSize) sample *= 2

    return BitmapFactory.decodeFile(
        file.path,
        BitmapFactory.Options().apply { inSampleSize = sample },
    )
}

private const val ThumbnailSize = 256

private val Options = BitmapFactory.Options().apply { inSampleSize = 10 }
