package com.fserver.app.presentation.shared.browser

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi


/** What a tap and a long press on one file do, given whether a selection is running. */
@Immutable
internal class FileGestures(
    val onClick: () -> Unit,
    val onLongClick: (() -> Unit)?,
)

internal fun fileGestures(
    file: FileBrowserUi.File,
    selection: FileBrowserSelection?,
    onFileClick: (FileBrowserUi.File) -> Unit,
    onFileLongClick: ((FileBrowserUi.File) -> Unit)?,
): FileGestures = if (selection != null) {
    FileGestures(onClick = { selection.onToggle(file) }, onLongClick = { onFileClick(file) })
} else {
    FileGestures(onClick = { onFileClick(file) }, onLongClick = onFileLongClick?.let { { it(file) } })
}
