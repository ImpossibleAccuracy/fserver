package com.fserver.app.presentation.shared.browser.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.app.presentation.designkit.DkMediaTile
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.shared.browser.FileBrowserSelection
import com.fserver.app.presentation.shared.browser.fileGestures
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.shared.viewer.FileThumbnail

/** One file as a tile. Without [onFileClick] it is a picture only, as in a preview strip. */
@Composable
fun BrowserGalleryTile(
    modifier: Modifier = Modifier,
    file: FileBrowserUi.File,
    selection: FileBrowserSelection?,
    onFileClick: ((FileBrowserUi.File) -> Unit)?,
    onFileLongClick: ((FileBrowserUi.File) -> Unit)? = null,
) {
    val gestures = onFileClick?.let { fileGestures(file, selection, it, onFileLongClick) }
    val hasThumbnail = file.kind.isMedia
    var isThumbnailLoaded by remember(file.locator) { mutableStateOf(false) }

    Box(modifier = modifier.aspectRatio(1f)) {
        DkMediaTile(
            extensionLabel = file.extensionLabel.takeUnless { isThumbnailLoaded },
            thumbnail = if (hasThumbnail) {
                {
                    FileThumbnail(
                        modifier = Modifier.matchParentSize(),
                        file = file,
                        onLoaded = { isThumbnailLoaded = true },
                    )
                }
            } else {
                null
            },
            label = file.name.takeUnless { file.kind == FileKindUi.Image },
            onClick = gestures?.onClick,
            onLongClick = gestures?.onLongClick,
        )

        if (file.isRemoteOnly) {
            RemoteOnlyBadge(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(DkSpacing.xs)
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
                        shape = MaterialTheme.shapes.small,
                    )
                    .padding(DkSpacing.xxs),
                file = file,
            )
        }

        // On the tile rather than beside it: a grid has no gutter to put a control in.
        FileCheckbox(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(DkSpacing.xs),
            file = file,
            selection = selection,
        )
    }
}
