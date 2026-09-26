package com.fserver.app.presentation.shared.browser.layouts

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.shared.browser.FileBrowserSelection
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi

/**
 * The pieces every layout is built from, so a file looks and measures the same in a list, a tree
 * and a gallery: switching between them moves rows, it does not resize them.
 */
@Immutable
internal class BrowserActions(
    val selection: FileBrowserSelection?,
    val onOpenDirectory: ((FileBrowserUi.Directory) -> Unit)?,
    val onFileClick: (FileBrowserUi.File) -> Unit,
    val onFileLongClick: ((FileBrowserUi.File) -> Unit)?,
)
