package com.fserver.app.presentation.shared.browser.layouts

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.shared.browser.FileBrowserHeader
import com.fserver.app.presentation.shared.browser.FileBrowserSelection
import com.fserver.app.presentation.shared.browser.composable.BrowserGalleryTile
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize

private const val HeaderKey = "header"

private val GridSpacing = 3.dp
private const val GridColumns = 3

/** Tiles: a gallery is judged by what it looks like, so nothing but the files is on screen. */
@Composable
fun BrowserGallery(
    modifier: Modifier = Modifier,
    preview: FileBrowserUi.Gallery,
    selection: FileBrowserSelection? = null,
    contentPadding: PaddingValues = PaddingValues(),
    header: @Composable (() -> Unit)? = null,
    onFileClick: (FileBrowserUi.File) -> Unit,
    onFileLongClick: ((FileBrowserUi.File) -> Unit)? = null,
) {
    val actions = BrowserActions(
        selection = selection,
        onOpenDirectory = null,
        onFileClick = onFileClick,
        onFileLongClick = onFileLongClick,
    )

    BrowserGrid(modifier = modifier, contentPadding = contentPadding) {
        if (header != null) {
            item(key = HeaderKey, span = { GridItemSpan(maxLineSpan) }) { header() }
        }

        entryTiles(preview.files, actions)
    }
}


/** The grid every tiled layout sits in; [content] fills it with [entryTiles]. */
@Composable
internal fun BrowserGrid(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues,
    content: LazyGridScope.() -> Unit,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(GridColumns),
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            horizontal = DkSpacing.screenPadding,
            vertical = DkSpacing.sm,
        ) + contentPadding,
        horizontalArrangement = Arrangement.spacedBy(GridSpacing),
        verticalArrangement = Arrangement.spacedBy(GridSpacing),
        content = content,
    )
}

/** Files as tiles. */
internal fun LazyGridScope.entryTiles(
    files: List<FileBrowserUi.File>,
    actions: BrowserActions,
) {
    items(files, key = { it.path }, contentType = { "tile" }) { file ->
        BrowserGalleryTile(
            file = file,
            selection = actions.selection,
            onFileClick = actions.onFileClick,
            onFileLongClick = actions.onFileLongClick,
        )
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
