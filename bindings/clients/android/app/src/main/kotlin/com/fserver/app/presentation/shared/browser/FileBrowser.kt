package com.fserver.app.presentation.shared.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.fserver.app.R
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.shared.browser.layouts.BrowserGallery
import com.fserver.app.presentation.shared.browser.layouts.BrowserList
import com.fserver.app.presentation.shared.browser.layouts.BrowserTree
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi

/**
 * What turns a preview into a picker. Left out, the preview is read-only.
 *
 * A layout offers a radio for whichever kind has a callback and none for the other, so a screen
 * that narrows a scan to one folder passes only [onSelectDirectory] and the files stay taps that
 * open them.
 *
 * [selected] is matched against a directory's `path` or a file's `locator` — the device-local
 * address in both cases, which is what a caller does something with afterward.
 */
@Immutable
class FileBrowserSelection(
    val selected: FileBrowserUi.PreviewContentEntry?,
    val onSelectFile: ((FileBrowserUi.File) -> Unit)? = null,
    val onSelectDirectory: ((FileBrowserUi.Directory) -> Unit)? = null,
    /** Open parent directory of the selected entry, if any */
    val walkUp: (() -> Unit)? = null,
)

/**
 * What the scan found, in the shape the source reads best: tiles for a gallery, rows for a folder,
 * a tree for a whole device.
 *
 * The counts are on the header rather than the rows because the question this answers is "is this
 * the right pile of files", not "what is in each of them". Tapping a file opens it whatever the
 * layout; picking one is a separate gesture, and only when [selection] says so.
 */
@Composable
fun FileBrowser(
    modifier: Modifier = Modifier,
    preview: FileBrowserUi,
    selection: FileBrowserSelection? = null,
    header: @Composable (() -> Unit)? = null,
    onFileClick: (FileBrowserUi.File) -> Unit,
) {
    if (preview.isEmpty) {
        FileBrowserEmpty(modifier = modifier, header = header)
        return
    }

    when (preview) {
        is FileBrowserUi.Gallery -> BrowserGallery(
            modifier = modifier,
            preview = preview,
            selection = selection,
            onFileClick = onFileClick,
            header = header,
        )

        is FileBrowserUi.PlainList -> BrowserList(
            modifier = modifier,
            preview = preview,
            selection = selection,
            onFileClick = onFileClick,
            header = header,
        )

        is FileBrowserUi.Tree -> BrowserTree(
            modifier = modifier,
            preview = preview,
            onFileClick = onFileClick,
            selection = selection,
            header = header,
        )
    }
}


/** The header every layout puts above the content: where it came from, and how much. */
@Composable
fun FileBrowserHeader(
    title: String,
    detail: String,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = DkSpacing.screenPadding, vertical = DkSpacing.md),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.xxs),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
        )
        DkCaption(text = detail)
    }
}

@Composable
private fun FileBrowserEmpty(
    modifier: Modifier = Modifier,
    header: @Composable (() -> Unit)? = null,
) {
    Column(modifier = modifier.fillMaxSize()) {
        header?.invoke()
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = DkSpacing.screenPadding),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = stringResource(R.string.file_browser_empty),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
    }
}
