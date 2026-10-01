package com.fserver.app.presentation.shared.browser.composable

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.formatted
import com.fserver.app.presentation.designkit.DkInlineSpinner
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkStatusDot
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.shared.browser.FileBrowserSelection
import com.fserver.app.presentation.shared.browser.fileGestures
import com.fserver.app.presentation.shared.browser.icon
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.shared.viewer.FileThumbnail

internal val EntryThumbnailSize = 48.dp

/**
 * One file as a row, the same in every layout: tick while a selection runs, its picture, size
 * and, when the caller tracks it, how the copy to the peer is getting on.
 */
@Composable
internal fun BrowserFileRow(
    modifier: Modifier = Modifier,
    file: FileBrowserUi.File,
    selection: FileBrowserSelection?,
    onFileClick: (FileBrowserUi.File) -> Unit,
    onFileLongClick: ((FileBrowserUi.File) -> Unit)?,
    menu: (@Composable (FileBrowserUi.File) -> Unit)? = null,
) {
    val gestures = fileGestures(file, selection, onFileClick, onFileLongClick)
    val sync = file.sync

    DkListRow(
        modifier = modifier,
        title = file.name,
        subtitle = listOfNotNull(
            file.size?.formatted(),
            sync?.let { stringResource(it.labelRes) },
            sync?.progress?.let { stringResource(R.string.file_sync_percent, (it * 100).toInt()) },
        )
            .joinToString(" · ")
            .ifEmpty { null },
        subtitleColor = if (sync?.isProblem == true) MaterialTheme.colorScheme.error else null,
        subtitleLeading = sync?.let { { DkStatusDot(color = it.color()) } },
        leading = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
            ) {
                FileCheckbox(file = file, selection = selection)
                EntryThumbnail(file = file)
            }
        },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (sync is FileBrowserUi.File.Sync.Receiving) {
                    DkInlineSpinner(progress = sync.progress)
                } else {
                    RemoteOnlyBadge(file = file)
                }
                if (selection == null) menu?.invoke(file)
            }
        },
        onClick = gestures.onClick,
        onLongClick = gestures.onLongClick,
    )
}

/** The kind icon, covered by the file's own picture once one loads. */
@Composable
private fun EntryThumbnail(
    modifier: Modifier = Modifier,
    file: FileBrowserUi.File,
) {
    Box(modifier = modifier) {
        DkThumbnail(icon = file.kind.icon(), size = EntryThumbnailSize)
        if (file.kind.isMedia) {
            FileThumbnail(
                modifier = Modifier
                    .matchParentSize()
                    .clip(MaterialTheme.shapes.medium),
                file = file,
            )
        }
    }
}

private val FileBrowserUi.File.Sync.labelRes: Int
    get() = when (this) {
        FileBrowserUi.File.Sync.Waiting -> R.string.file_sync_waiting
        is FileBrowserUi.File.Sync.Sending -> R.string.file_sync_sending
        is FileBrowserUi.File.Sync.Receiving -> R.string.file_sync_receiving
        FileBrowserUi.File.Sync.Failed -> R.string.file_sync_failed
    }

private val FileBrowserUi.File.Sync.progress: Float?
    get() = when (this) {
        is FileBrowserUi.File.Sync.Sending -> progress
        is FileBrowserUi.File.Sync.Receiving -> progress
        FileBrowserUi.File.Sync.Waiting,
        FileBrowserUi.File.Sync.Failed -> null
    }

private val FileBrowserUi.File.Sync.isProblem: Boolean
    get() = this == FileBrowserUi.File.Sync.Waiting || this == FileBrowserUi.File.Sync.Failed

@Composable
private fun FileBrowserUi.File.Sync.color(): Color =
    if (isProblem) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary

