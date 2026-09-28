package com.fserver.app.presentation.shared.browser.layouts

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.layout
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.shared.browser.FileBrowserNavigation
import com.fserver.app.presentation.shared.browser.FileBrowserSelection
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize
import com.fserver.common.utils.SourcePaths


/**
 * Folders, opened one at a time. Walking them is [navigation]'s job and ticking files is
 * [selection]'s; the two are independent, so a selection survives walking in and out of folders.
 */
@Composable
fun BrowserTree(
    modifier: Modifier = Modifier,
    preview: FileBrowserUi.Tree,
    navigation: FileBrowserNavigation? = null,
    selection: FileBrowserSelection? = null,
    contentPadding: PaddingValues = PaddingValues(),
    header: @Composable (() -> Unit)? = null,
    onFileClick: (FileBrowserUi.File) -> Unit,
    onFileLongClick: ((FileBrowserUi.File) -> Unit)? = null,
    fileMenu: (@Composable (FileBrowserUi.File) -> Unit)? = null,
) {
    // A caller that does not show where the walk is still gets one, kept here instead.
    val walk = navigation ?: rememberTreeNavigation(preview)
    val opened = walk.opened

    // A running selection is the caller's to close first.
    BackHandler(enabled = opened != null && selection == null, onBack = walk.onUp)

    Column(modifier = modifier.fillMaxSize()) {
        if (header != null) {
            header()
        }

        AnimatedContent(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            targetState = opened,
            contentKey = { it?.path },
        ) { directory ->
            val actions = BrowserActions(
                selection = selection,
                onOpenDirectory = walk.onOpen,
                onFileClick = onFileClick,
                onFileLongClick = onFileLongClick,
                fileMenu = fileMenu,
            )

            if (directory?.isMediaDirectory == true) {
                val (media, rest) = directory.contents.partition { it is FileBrowserUi.File && it.kind.isMedia }

                BrowserGrid(contentPadding = contentPadding) {
                    entryRows(rest, actions)
                    entryTiles(media.filterIsInstance<FileBrowserUi.File>(), actions)
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = BrowserListPadding + contentPadding,
                ) {
                    entryRows(directory?.contents ?: preview.rootContents, actions)
                }
            }
        }
    }
}

/** Entries as full-width rows inside a grid, measuring exactly as they do in a list. */
internal fun LazyGridScope.entryRows(
    entries: List<FileBrowserUi.PreviewContentEntry>,
    actions: BrowserActions,
) {
    items(
        items = entries,
        key = { it.path },
        contentType = { "row" },
        span = { GridItemSpan(maxLineSpan) },
    ) { entry ->
        BrowserEntryRow(
            modifier = Modifier.bleed(DkSpacing.screenPadding),
            entry = entry,
            actions = actions,
        )
    }
}

/** Widens a row past the grid's gutter, so it is as full-bleed as in a list. */
private fun Modifier.bleed(gutter: Dp): Modifier = layout { measurable, constraints ->
    val extra = gutter.roundToPx()
    val placeable = measurable.measure(
        constraints.copy(
            minWidth = constraints.minWidth + extra * 2,
            maxWidth = constraints.maxWidth + extra * 2,
        )
    )
    layout(constraints.maxWidth, placeable.height) { placeable.place(-extra, 0) }
}

/** The walk a tree does on its own: open a folder, back out of it. */
@Composable
private fun rememberTreeNavigation(preview: FileBrowserUi.Tree): FileBrowserNavigation {
    var opened by remember(preview) { mutableStateOf<FileBrowserUi.Directory?>(null) }

    return remember(preview, opened) {
        FileBrowserNavigation(
            opened = opened,
            onOpen = { opened = it },
            onUp = { opened = preview.parentOf(opened) },
        )
    }
}

@Composable
fun FileBrowserUi.Directory.displayLabel(): String =
    if (isVolume && name == SourcePaths.PrimaryVolume) {
        stringResource(R.string.file_browser_volume_primary)
    } else {
        name
    }

@Preview(name = "Tree", showBackground = true)
@Composable
private fun FileBrowserTreePreview() {
    FServerTheme {
        BrowserTree(
            preview = FileBrowserUi.Tree(
                directories = SampleVolumes,
            ),
            onFileClick = {},
        )
    }
}

private val SampleVolumes = listOf(
    FileBrowserUi.Directory(
        path = "/storage/emulated/0",
        name = SourcePaths.PrimaryVolume,
        files = 12_408,
        size = FileSize(41_200_000_000L),
        isVolume = true,
        contents = listOf(
            FileBrowserUi.Directory(
                path = "/storage/emulated/0/DCIM",
                name = "DCIM",
                files = 2_310,
                size = FileSize(19_100_000_000L),
                contents = listOf(
                    FileBrowserUi.Directory(
                        path = "/storage/emulated/0/DCIM/Camera",
                        name = "Camera",
                        files = 2_140,
                        size = FileSize(18_400_000_000L),
                        contents = listOf(),
                    ),
                ),
            ),
            FileBrowserUi.Directory(
                path = "/storage/emulated/0/Download",
                name = "Download",
                files = 87,
                size = FileSize(1_240_000_000L),
                contents = listOf(),
            ),
        ),
    ),
    FileBrowserUi.Directory(
        path = "/storage/1B0C-4F2A",
        name = "1B0C-4F2A",
        files = 640,
        size = FileSize(8_900_000_000L),
        isVolume = true,
    ),
    FileBrowserUi.File(
        id = "1",
        path = "primary/DCIM/Camera/IMG_0001.jpg",
        name = "IMG_0001.jpg",
        kind = FileKindUi.Image,
        locator = "/storage/emulated/0/DCIM/Camera/IMG_0001.jpg",
        size = FileSize(4_210_000),
        extensionLabel = null,
    ),
)
