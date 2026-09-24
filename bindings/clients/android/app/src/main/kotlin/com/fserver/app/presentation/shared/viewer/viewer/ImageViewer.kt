package com.fserver.app.presentation.shared.viewer.viewer

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalWindowInfo
import coil3.compose.LocalPlatformContext
import coil3.compose.rememberAsyncImagePainter
import coil3.request.ImageRequest
import coil3.size.Size
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.shared.viewer.fileViewerContent
import com.fserver.app.presentation.shared.viewer.thumbnailCacheKey

/**
 * The image fitted to the screen. Sized to the image itself rather than the screen, so the shared
 * element morphs between two pictures, not between a picture and a letterbox.
 *
 * The tile's thumbnail stands in until the full image decodes, which also gives the aspect ratio
 * from the very first frame.
 */
@Composable
internal fun ImageViewer(
    modifier: Modifier = Modifier,
    file: FileBrowserUi.File,
    animatedVisibilityScope: AnimatedVisibilityScope,
) {
    val context = LocalPlatformContext.current
    val screen = LocalWindowInfo.current.containerSize
    val request = remember(context, file.locator, screen) {
        ImageRequest.Builder(context)
            .data(file.localUri())
            .placeholderMemoryCacheKey(file.thumbnailCacheKey)
            .size(Size(screen.width, screen.height))
            .build()
    }
    val painter = rememberAsyncImagePainter(model = request)
    val intrinsic = painter.intrinsicSize
    val ratio = if (intrinsic.isSpecified && intrinsic.width > 0f && intrinsic.height > 0f) {
        intrinsic.width / intrinsic.height
    } else {
        null
    }

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Image(
            modifier = Modifier
                .then(if (ratio != null) Modifier.aspectRatio(ratio) else Modifier.fillMaxSize())
                .fileViewerContent(file, animatedVisibilityScope),
            painter = painter,
            contentDescription = file.name,
            contentScale = ContentScale.Crop,
        )
    }
}
