package com.fserver.app.presentation.shared.browser.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
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
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkSpacing
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
) {
    val gestures = fileGestures(file, selection, onFileClick, onFileLongClick)
    val sync = file.sync

    DkListRow(
        modifier = modifier,
        title = file.name,
        subtitle = listOfNotNull(file.size?.formatted(), sync?.let { stringResource(it.labelRes) })
            .joinToString(" · ")
            .ifEmpty { null },
        subtitleColor = if (sync == FileBrowserUi.File.Sync.Waiting) MaterialTheme.colorScheme.error else null,
        subtitleLeading = sync?.let { { SyncDot(color = it.color()) } },
        leading = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
            ) {
                FileCheckbox(file = file, selection = selection)
                EntryThumbnail(file = file)
            }
        },
        trailing = { RemoteOnlyBadge(file = file) },
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
        FileBrowserUi.File.Sync.Sending -> R.string.file_sync_sending
    }

@Composable
private fun FileBrowserUi.File.Sync.color(): Color = when (this) {
    FileBrowserUi.File.Sync.Waiting -> MaterialTheme.colorScheme.error
    FileBrowserUi.File.Sync.Sending -> MaterialTheme.colorScheme.primary
}

@Composable
private fun SyncDot(modifier: Modifier = Modifier, color: Color) {
    Box(
        modifier = modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color),
    )
}
