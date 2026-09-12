package com.fserver.app.presentation.screens.source.shared.preview.composable.layouts

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.app.presentation.composable.model.formatted
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.screens.source.shared.preview.composable.FileRadio
import com.fserver.app.presentation.screens.source.shared.preview.composable.SourcePreviewSelection
import com.fserver.app.presentation.screens.source.shared.preview.composable.icon
import com.fserver.app.presentation.screens.source.shared.preview.model.SourcePreviewUi
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize
import com.fserver.common.utils.SourcePaths


@Composable
fun PreviewTree(
    modifier: Modifier = Modifier,
    preview: SourcePreviewUi.Tree,
    selection: SourcePreviewSelection?,
    header: @Composable (() -> Unit)? = null,
    onFileClick: (SourcePreviewUi.File) -> Unit,
) {
    // Walking the folders is what a tree is for, picking one out of it is not: a caller that owns
    // no selection still gets the walk, kept here instead.
    val walk = selection ?: rememberTreeWalk(preview)

    val visibleDirectory = remember(walk.selected) {
        walk.selected as? SourcePreviewUi.Directory
    }

    BackHandler(
        enabled = visibleDirectory != null && walk.walkUp != null,
        onBack = { walk.walkUp?.invoke() },
    )

    Column(modifier = modifier.fillMaxSize()) {
        if (header != null) {
            header()
        }

        AnimatedContent(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            targetState = visibleDirectory,
            contentKey = { it?.path },
        ) { directory ->
            if (directory?.isMediaDirectory == true) {
                EntriesGrid(
                    modifier = Modifier.fillMaxSize(),
                    visibleContent = directory.contents,
                    selection = walk,
                    onFileClick = onFileClick,
                )
            } else {
                EntriesList(
                    modifier = Modifier.fillMaxSize(),
                    visibleContent = directory?.contents ?: preview.rootContents,
                    selection = walk,
                    onFileClick = onFileClick,
                )
            }
        }
    }
}

/** The walk a read-only tree does on its own: open a folder, back out of it, nothing selected. */
@Composable
private fun rememberTreeWalk(preview: SourcePreviewUi.Tree): SourcePreviewSelection {
    var opened by remember(preview) { mutableStateOf<SourcePreviewUi.Directory?>(null) }

    return remember(preview, opened) {
        SourcePreviewSelection(
            selected = opened,
            onSelectDirectory = { opened = it },
            walkUp = { opened = preview.parentOf(opened) },
        )
    }
}

@Composable
private fun EntriesGrid(
    modifier: Modifier = Modifier,
    visibleContent: List<SourcePreviewUi.PreviewContentEntry>,
    selection: SourcePreviewSelection?,
    onFileClick: (SourcePreviewUi.File) -> Unit,
) {
    val (mediaItems, regularItems) = visibleContent.partition {
        when (it) {
            is SourcePreviewUi.Directory -> false
            is SourcePreviewUi.File -> it.kind.isMedia
        }
    }

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
        items(
            items = regularItems,
            key = { it.path },
            contentType = { if (it is SourcePreviewUi.Directory) "directory" else "file" },
            span = { GridItemSpan(maxLineSpan) }
        ) {
            EntryListItem(
                row = it,
                selection = selection,
                onFileClick = onFileClick,
            )
        }

        items(
            items = mediaItems,
            key = { it.path },
            contentType = { if (it is SourcePreviewUi.Directory) "directory" else "file" },
        ) {
            if (it !is SourcePreviewUi.File) return@items // GridItemSpan is only for files, directories are always full-width

            PreviewGalleryTile(
                file = it,
                selection = selection,
                onFileClick = onFileClick,
            )
        }
    }
}

@Composable
private fun EntriesList(
    modifier: Modifier = Modifier,
    visibleContent: List<SourcePreviewUi.PreviewContentEntry>,
    selection: SourcePreviewSelection?,
    onFileClick: (SourcePreviewUi.File) -> Unit,
) {
    LazyColumn(
        modifier = modifier,
        contentPadding = PaddingValues(vertical = DkSpacing.xs),
    ) {
        items(items = visibleContent, key = { it.path }) { row ->
            EntryListItem(
                row = row,
                selection = selection,
                onFileClick = onFileClick,
            )
        }
    }
}

@Composable
private fun EntryListItem(
    selection: SourcePreviewSelection?,
    row: SourcePreviewUi.PreviewContentEntry,
    onFileClick: (SourcePreviewUi.File) -> Unit,
) {
    when (row) {
        is SourcePreviewUi.Directory -> {
            DkListRow(
                title = row.displayLabel(),
                subtitle = stringResource(
                    R.string.source_preview_directory_count,
                    row.files,
                    row.size.formatted(),
                ),
                leading = {
                    DkThumbnail(
                        icon = Icons.Default.FolderOpen,
                        size = 48.dp,
                    )
                },
                trailing = {
                    DkIcon(
                        icon = Icons.Default.ChevronRight,
                        size = 16.dp,
                    )
                },
                onClick = { selection?.onSelectDirectory?.invoke(row) },
            )
        }

        is SourcePreviewUi.File -> DkListRow(
            title = row.name,
            titleMaxLines = 2,
            subtitle = row.size?.formatted(),
            onClick = { onFileClick(row) },
            leading = {
                DkThumbnail(
                    icon = row.kind.icon(),
                    size = 48.dp,
                )
            },
            trailing = { FileRadio(file = row, selection = selection) },
        )
    }
}

@Composable
fun SourcePreviewUi.Directory.displayLabel(): String =
    if (isVolume && name == SourcePaths.PrimaryVolume) {
        stringResource(R.string.source_preview_volume_primary)
    } else {
        name
    }

@Preview(name = "Tree", showBackground = true)
@Composable
private fun SourcePreviewTreePreview() {
    FServerTheme {
        PreviewTree(
            preview = SourcePreviewUi.Tree(
                directories = SampleVolumes,
            ),
            onFileClick = {},
            selection = SourcePreviewSelection(
                selected = null,
                onSelectDirectory = {},
            ),
        )
    }
}

private val SampleVolumes = listOf(
    SourcePreviewUi.Directory(
        path = "/storage/emulated/0",
        name = SourcePaths.PrimaryVolume,
        files = 12_408,
        size = FileSize(41_200_000_000L),
        isVolume = true,
        contents = listOf(
            SourcePreviewUi.Directory(
                path = "/storage/emulated/0/DCIM",
                name = "DCIM",
                files = 2_310,
                size = FileSize(19_100_000_000L),
                contents = listOf(
                    SourcePreviewUi.Directory(
                        path = "/storage/emulated/0/DCIM/Camera",
                        name = "Camera",
                        files = 2_140,
                        size = FileSize(18_400_000_000L),
                        contents = listOf(),
                    ),
                ),
            ),
            SourcePreviewUi.Directory(
                path = "/storage/emulated/0/Download",
                name = "Download",
                files = 87,
                size = FileSize(1_240_000_000L),
                contents = listOf(),
            ),
        ),
    ),
    SourcePreviewUi.Directory(
        path = "/storage/1B0C-4F2A",
        name = "1B0C-4F2A",
        files = 640,
        size = FileSize(8_900_000_000L),
        isVolume = true,
    ),
    SourcePreviewUi.File(
        id = "1",
        path = "primary/DCIM/Camera/IMG_0001.jpg",
        name = "IMG_0001.jpg",
        kind = FileKindUi.Image,
        locator = "/storage/emulated/0/DCIM/Camera/IMG_0001.jpg",
        size = FileSize(4_210_000),
        extensionLabel = null,
    ),
)
