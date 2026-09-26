package com.fserver.app.presentation.shared.browser

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
 * Which folder of a tree is open, and the way in and out of one. Hoisted by a caller that shows
 * where the walk is (a title, breadcrumbs) or acts on the open folder; left out, the tree keeps
 * its own walk.
 */
@Immutable
class FileBrowserNavigation(
    val opened: FileBrowserUi.Directory?,
    val onOpen: (FileBrowserUi.Directory) -> Unit,
    val onUp: () -> Unit,
)

/**
 * Files ticked for an action, by [FileBrowserUi.File.id]. While there is one, a tap ticks and a
 * long press opens; without one, a tap opens.
 */
@Immutable
class FileBrowserSelection(
    val selected: Set<String>,
    val onToggle: (FileBrowserUi.File) -> Unit,
) {
    fun isSelected(file: FileBrowserUi.File): Boolean = file.id in selected
}

/**
 * What the scan found, in the shape the source reads best: tiles for a gallery, rows for a folder,
 * a tree for a whole device.
 *
 * The counts are on the header rather than the rows because the question this answers is "is this
 * the right pile of files", not "what is in each of them". Tapping a file opens it whatever the
 * layout; [onFileLongClick] is the way into a [selection], which the caller owns.
 */
@Composable
fun FileBrowser(
    modifier: Modifier = Modifier,
    preview: FileBrowserUi,
    navigation: FileBrowserNavigation? = null,
    selection: FileBrowserSelection? = null,
    /** Added to each layout's own padding; for insets the list should scroll under. */
    contentPadding: PaddingValues = PaddingValues(),
    header: @Composable (() -> Unit)? = null,
    onFileClick: (FileBrowserUi.File) -> Unit,
    onFileLongClick: ((FileBrowserUi.File) -> Unit)? = null,
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
            contentPadding = contentPadding,
            onFileClick = onFileClick,
            onFileLongClick = onFileLongClick,
            header = header,
        )

        is FileBrowserUi.PlainList -> BrowserList(
            modifier = modifier,
            preview = preview,
            selection = selection,
            contentPadding = contentPadding,
            onFileClick = onFileClick,
            onFileLongClick = onFileLongClick,
            header = header,
        )

        is FileBrowserUi.Tree -> BrowserTree(
            modifier = modifier,
            preview = preview,
            navigation = navigation,
            selection = selection,
            contentPadding = contentPadding,
            onFileClick = onFileClick,
            onFileLongClick = onFileLongClick,
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
