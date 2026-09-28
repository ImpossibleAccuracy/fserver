package com.fserver.app.data.preview

import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Precision
import coil3.toBitmap
import com.fserver.app.presentation.composable.model.fileKindOf
import com.fserver.app.presentation.shared.viewer.impl.FileImage
import com.fserver.core.disk.StoreType
import com.fserver.core.files.preview.EvictingFile
import com.fserver.core.files.preview.EvictionPreviewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Makes previews with the app's own Coil loader, so a file gets one exactly when a thumbnail of it
 * would load: whatever decoders the loader has decide, nothing here looks at the file type.
 */
class CoilEvictionPreviewer(
    private val context: Context,
    private val previews: EvictionPreviews,
) : EvictionPreviewer {

    override suspend fun capture(file: EvictingFile) {
        val request = ImageRequest.Builder(context)
            .data(file.imageModel())
            .size(PreviewSize)
            .precision(Precision.INEXACT)
            // Read back as pixels below; and the bytes are about to go, so caching them is waste.
            .allowHardware(false)
            .memoryCachePolicy(CachePolicy.DISABLED)
            .diskCachePolicy(CachePolicy.DISABLED)
            .build()

        val result = SingletonImageLoader.get(context).execute(request)
        if (result !is SuccessResult) {
            Timber.d("No preview for ${file.path}")
            return
        }

        withContext(Dispatchers.IO) {
            previews.save(file.sourceId, file.fileId, result.image.toBitmap())
        }
    }

    override suspend fun usage(): Map<StoreType, Long> = withContext(Dispatchers.IO) {
        mapOf(StoreType.AppData to previews.bytes())
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { previews.clear() }
        // Tiles would keep showing the dropped pictures until the process dies otherwise.
        SingletonImageLoader.get(context).memoryCache?.clear()
    }

    private companion object {
        /** Enough for a full-width tile; the full picture comes back with the file. */
        const val PreviewSize = 512
    }
}

/** The same model a thumbnail loads, so the preview is what the tile showed. */
private fun EvictingFile.imageModel() = FileImage(
    sourceId = sourceId,
    fileId = fileId,
    locator = locator,
    kind = fileKindOf(path),
    version = hash.value,
)
