package com.fserver.app.presentation.shared.browser.layouts

import com.fserver.app.presentation.shared.browser.model.FileKey
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.plus
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.app.presentation.composable.model.formatted
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.shared.browser.FileBrowserHeader
import com.fserver.app.presentation.shared.browser.FileBrowserSelection
import com.fserver.app.presentation.shared.browser.composable.BrowserFileRow
import com.fserver.app.presentation.shared.browser.composable.EntryCheckbox
import com.fserver.app.presentation.shared.browser.composable.EntryThumbnailSize
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize

private const val HeaderKey = "header"

internal val BrowserListPadding = PaddingValues(vertical = DkSpacing.xs)

/** Rows: name, size, and what kind of file it is. The row is the touch target that opens it. */
@Composable
fun BrowserList(
    modifier: Modifier = Modifier,
    preview: FileBrowserUi.PlainList,
    selection: FileBrowserSelection? = null,
    contentPadding: PaddingValues = PaddingValues(),
    header: @Composable (() -> Unit)? = null,
    onFileClick: (FileBrowserUi.File) -> Unit,
    onFileLongClick: ((FileBrowserUi.File) -> Unit)? = null,
    fileMenu: (@Composable (FileBrowserUi.File) -> Unit)? = null,
) {
    val actions = BrowserActions(
        selection = selection,
        onOpenDirectory = null,
        onFileClick = onFileClick,
        onFileLongClick = onFileLongClick,
        fileMenu = fileMenu,
    )

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = BrowserListPadding + contentPadding,
    ) {
        if (header != null) {
            item(key = HeaderKey) { header() }
        }

        entryRows(preview.files, actions)
    }
}

/** Rows, one per entry, divided the way every list in the app is. */
internal fun LazyListScope.entryRows(
    entries: List<FileBrowserUi.PreviewContentEntry>,
    actions: BrowserActions,
) {
    itemsIndexed(entries, key = { _, entry -> entry.path }) { index, entry ->
        BrowserEntryRow(entry = entry, actions = actions)
        if (index != entries.lastIndex) DkFadingDivider()
    }
}

@Composable
internal fun BrowserEntryRow(
    modifier: Modifier = Modifier,
    entry: FileBrowserUi.PreviewContentEntry,
    actions: BrowserActions,
) {
    when (entry) {
        is FileBrowserUi.Directory -> BrowserDirectoryRow(
            modifier = modifier,
            directory = entry,
            selection = actions.selection,
            onOpen = actions.onOpenDirectory,
            onLongClick = actions.onDirectoryLongClick,
        )

        is FileBrowserUi.File -> BrowserFileRow(
            modifier = modifier,
            file = entry,
            selection = actions.selection,
            onFileClick = actions.onFileClick,
            onFileLongClick = actions.onFileLongClick,
            menu = actions.fileMenu,
        )
    }
}

@Composable
internal fun BrowserDirectoryRow(
    modifier: Modifier = Modifier,
    directory: FileBrowserUi.Directory,
    selection: FileBrowserSelection? = null,
    onOpen: ((FileBrowserUi.Directory) -> Unit)?,
    onLongClick: ((FileBrowserUi.Directory) -> Unit)? = null,
) {
    val toggle = selection?.onToggleDirectory
    val open = onOpen?.let { { it(directory) } }

    DkListRow(
        modifier = modifier,
        title = directory.displayLabel(),
        subtitle = stringResource(
            R.string.file_browser_directory_count,
            directory.files,
            directory.size.formatted(),
        ),
        leading = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
            ) {
                EntryCheckbox(
                    visible = toggle != null,
                    checked = selection?.isSelected(directory) == true,
                    onCheckedChange = { toggle?.invoke(directory) },
                )
                DkThumbnail(icon = Icons.Default.FolderOpen, size = EntryThumbnailSize)
            }
        },
        trailing = { DkIcon(icon = Icons.Default.ChevronRight, size = 16.dp) },
        onClick = if (toggle != null) {
            { toggle(directory) }
        } else {
            open
        },
        onLongClick = when {
            toggle != null -> open
            selection == null -> onLongClick?.let { { it(directory) } }
            else -> null
        },
    )
}

@Preview(name = "List", showBackground = true)
@Composable
private fun FileBrowserListPreview() {
    FServerTheme {
        BrowserList(
            preview = FileBrowserUi.PlainList(
                files = listOf(SampleImage, SampleDocument),
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

@Preview(name = "List, selectable", showBackground = true)
@Composable
private fun FileBrowserSelectableListPreview() {
    FServerTheme {
        BrowserList(
            preview = FileBrowserUi.PlainList(
                files = listOf(SampleImage, SampleDocument),
            ),
            onFileClick = {},
            selection = FileBrowserSelection(
                selected = setOf(SampleImage.indexedKey),
                onToggle = {},
            ),
        )
    }
}

private val SampleImage = FileBrowserUi.File(
    key = FileKey(fileId = "1", sourceId = "camera"),
    path = "primary/DCIM/Camera/IMG_0001.jpg",
    name = "IMG_0001.jpg",
    kind = FileKindUi.Image,
    locator = "/storage/emulated/0/DCIM/Camera/IMG_0001.jpg",
    size = FileSize(4_210_000),
    extensionLabel = null,
    sync = FileBrowserUi.File.Sync.Waiting,
)

private val SampleDocument = FileBrowserUi.File(
    key = FileKey(fileId = "2", sourceId = "camera"),
    path = "primary/Documents/report.pdf",
    name = "report.pdf",
    kind = FileKindUi.Document,
    locator = "/storage/emulated/0/Documents/report.pdf",
    size = FileSize(820_000),
    extensionLabel = "PDF",
)
