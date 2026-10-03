package com.fserver.app.data.preview

import com.fserver.app.presentation.shared.browser.model.FileKey
import android.content.Context
import coil3.SingletonImageLoader
import coil3.request.CachePolicy
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import com.fserver.app.presentation.composable.model.fileKindOf
import com.fserver.app.presentation.shared.viewer.impl.FileImage
import com.fserver.app.presentation.shared.viewer.impl.imageVersionOf
import com.fserver.app.presentation.shared.viewer.impl.keepPreview
import com.fserver.app.presentation.shared.viewer.impl.mimeTypeOf
import com.fserver.core.disk.StoreType
import com.fserver.core.files.preview.EvictingFile
import com.fserver.core.files.preview.EvictionPreviewer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Makes previews with the app's own Coil loader, so a file gets one exactly when a thumbnail of it
 * would load: whatever decoders the loader has decide, nothing here looks at the file type. The
 * fetcher stores what it decoded in the loader's own disk cache - see [EvictionPreviews].
 */
class CoilEvictionPreviewer(
    private val context: Context,
    private val previews: EvictionPreviews,
) : EvictionPreviewer {

    override suspend fun capture(file: EvictingFile) {
        val request = ImageRequest.Builder(context)
            .data(file.imageModel())
            .keepPreview()
            // The bytes are about to go, so holding the picture in memory is waste.
            .memoryCachePolicy(CachePolicy.DISABLED)
            .build()

        if (SingletonImageLoader.get(context).execute(request) !is SuccessResult) {
            Timber.d("No preview for ${file.path}")
        }
    }

    override suspend fun usage(): Map<StoreType, Long> = withContext(Dispatchers.IO) {
        mapOf(StoreType.AppData to previews.diskCache.size)
    }

    override suspend fun clear() {
        withContext(Dispatchers.IO) { previews.diskCache.clear() }
        // Tiles would keep showing the dropped pictures until the process dies otherwise.
        SingletonImageLoader.get(context).memoryCache?.clear()
    }

}

/** The same model a thumbnail loads, so the preview is what the tile showed. */
private fun EvictingFile.imageModel() = FileImage(
    file = FileKey(fileId = fileId, sourceId = sourceId),
    locator = locator,
    kind = fileKindOf(path),
    mimeType = mimeTypeOf(path),
    version = imageVersionOf(modifiedAt, size),
)
