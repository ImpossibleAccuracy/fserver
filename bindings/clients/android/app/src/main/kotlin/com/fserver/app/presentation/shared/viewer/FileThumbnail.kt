package com.fserver.app.presentation.shared.viewer

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import coil3.compose.LocalPlatformContext
import coil3.request.ImageRequest
import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.shared.viewer.impl.AudioArtwork
import com.fserver.app.presentation.shared.viewer.viewer.localUri

/**
 * A cropped preview for a tile: an image, a video frame, or an audio file's embedded artwork. Tied
 * to [FileViewerHost], so the tile grows into the full-screen view and shrinks back into it.
 *
 * [onLoaded] fires once there is a picture — an audio file may well have none.
 */
@Composable
fun FileThumbnail(
    modifier: Modifier = Modifier,
    file: FileBrowserUi.File,
    onLoaded: () -> Unit = {},
) {
    val context = LocalPlatformContext.current
    val request = remember(context, file.locator) {
        ImageRequest.Builder(context)
            .data(file.imageModel())
            .memoryCacheKey(file.thumbnailCacheKey)
            .build()
    }

    AsyncImage(
        modifier = modifier.fileViewerThumbnail(file),
        model = request,
        contentDescription = null,
        contentScale = ContentScale.Crop,
        onSuccess = { onLoaded() },
    )
}

/** What Coil loads for [this]: the file itself, or for audio the picture in its tags. */
internal fun FileBrowserUi.File.imageModel(): Any? = localUri()?.let {
    if (kind == FileKindUi.Audio) AudioArtwork(it, version = "${modifiedAt?.toEpochMilliseconds()}:${size?.bytes}") else it
}

/** Whatever [FileViewerHost] shows now; thumbnails of it step aside for the full-screen copy. */
@Stable
internal class FileViewerTransition(
    val scope: SharedTransitionScope,
    private val viewed: () -> FileBrowserUi.File?,
) {
    fun isViewing(file: FileBrowserUi.File): Boolean = viewed()?.sharedKey == file.sharedKey
}

/** Null outside a [FileViewerHost] — a preview, say — where thumbnails simply don't animate. */
internal val LocalFileViewerTransition = compositionLocalOf<FileViewerTransition?> { null }

@Composable
private fun Modifier.fileViewerThumbnail(file: FileBrowserUi.File): Modifier {
    val transition = LocalFileViewerTransition.current ?: return this

    return with(transition.scope) {
        sharedElementWithCallerManagedVisibility(
            sharedContentState = rememberSharedContentState(file.sharedKey),
            visible = !transition.isViewing(file),
        )
    }
}

/** The full-screen side of [fileViewerThumbnail]. */
@Composable
internal fun Modifier.fileViewerContent(
    file: FileBrowserUi.File,
    animatedVisibilityScope: AnimatedVisibilityScope,
): Modifier {
    val transition = LocalFileViewerTransition.current ?: return this

    return with(transition.scope) {
        sharedElement(
            sharedContentState = rememberSharedContentState(file.sharedKey),
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
}

/** The viewer shows this as a placeholder while the full-size image decodes. */
internal val FileBrowserUi.File.thumbnailCacheKey: String
    get() = "thumbnail:$sharedKey"

private val FileBrowserUi.File.sharedKey: String
    get() = "file:${locator ?: path}"
