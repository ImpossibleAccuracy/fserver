package com.fserver.app.presentation.shared.browser.layouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.app.presentation.designkit.DkMediaTile
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.shared.browser.FileBrowserHeader
import com.fserver.app.presentation.shared.browser.FileBrowserSelection
import com.fserver.app.presentation.shared.browser.RemoteOnlyBadge
import com.fserver.app.presentation.shared.browser.FileRadio
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.shared.viewer.FileThumbnail
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize

private val GridItemSpanMax = GridItemSpan(3)
private const val HeaderKey = "header"

/** Tiles: a gallery is judged by what it looks like, so nothing but the files is on screen. */
@Composable
fun BrowserGallery(
    modifier: Modifier = Modifier,
    preview: FileBrowserUi.Gallery,
    selection: FileBrowserSelection? = null,
    header: @Composable (() -> Unit)? = null,
    onFileClick: (FileBrowserUi.File) -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = DkSpacing.screenPadding,
            vertical = DkSpacing.sm,
        ),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        if (header != null) {
            item(key = HeaderKey, span = { GridItemSpanMax }) { header() }
        }

        items(items = preview.files, key = { it.path }) { file ->
            BrowserGalleryTile(
                file = file,
                selection = selection,
                onFileClick = onFileClick,
            )
        }
    }
}

@Composable
fun BrowserGalleryTile(
    modifier: Modifier = Modifier,
    file: FileBrowserUi.File,
    selection: FileBrowserSelection?,
    onFileClick: (FileBrowserUi.File) -> Unit,
) {
    val hasThumbnail = file.kind.isMedia
    var isThumbnailLoaded by remember(file.locator) { mutableStateOf(false) }

    Box(modifier = modifier) {
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
            onClick = { onFileClick(file) },
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
        Box(modifier = Modifier.align(Alignment.TopStart)) {
            FileRadio(
                file = file,
                selection = selection
            )
        }
    }
}

@Preview(name = "Gallery", showBackground = true)
@Composable
private fun FileBrowserGalleryPreview() {
    FServerTheme {
        BrowserGallery(
            preview = FileBrowserUi.Gallery(
                files = listOf(SampleImage, SampleAudio, SampleDocument),
            ),
            onFileClick = {},
            header = {
                FileBrowserHeader(
                    title = "/DCIM/Projects",
                    detail = "842 files · 6.1 GB"
                )
            },
        )
    }
}

private val SampleImage = FileBrowserUi.File(
    id = "1",
    path = "primary/DCIM/Camera/IMG_0001.jpg",
    name = "IMG_0001.jpg",
    kind = FileKindUi.Image,
    locator = "/storage/emulated/0/DCIM/Camera/IMG_0001.jpg",
    size = FileSize(4_210_000),
    extensionLabel = null,
)

private val SampleAudio = FileBrowserUi.File(
    id = "3",
    path = "primary/Music/track.mp3",
    name = "track.mp3",
    kind = FileKindUi.Audio,
    locator = "/storage/emulated/0/Music/track.mp3",
    size = FileSize(6_300_000),
    extensionLabel = "MP3",
)

private val SampleDocument = FileBrowserUi.File(
    id = "2",
    path = "primary/Documents/report.pdf",
    name = "report.pdf",
    kind = FileKindUi.Document,
    locator = "/storage/emulated/0/Documents/report.pdf",
    size = FileSize(820_000),
    extensionLabel = "PDF",
)
