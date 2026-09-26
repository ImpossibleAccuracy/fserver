package com.fserver.app.presentation.screens.settings.storage.source.composable

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.formatted
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.screens.settings.storage.source.model.StorageSourceState
import com.fserver.app.presentation.screens.settings.storage.source.model.StorageSourceState.CopyStatusUi
import com.fserver.app.presentation.shared.browser.icon
import com.fserver.app.presentation.shared.viewer.FileThumbnail
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize

/**
 * One file a link keeps here: where its copy stands and what it weighs. A tap opens it and a long
 * press starts a selection; inside one the two swap, so a tap toggles and a long press still opens.
 */
@Composable
fun StorageFileRow(
    modifier: Modifier = Modifier,
    file: StorageSourceState.FileUi,
    deviceName: String,
    editing: Boolean,
    selected: Boolean,
    onOpen: () -> Unit,
    onToggle: () -> Unit,
    onLongPress: () -> Unit,
) {
    val waiting = file.status == CopyStatusUi.Waiting

    DkListRow(
        modifier = modifier,
        title = file.name,
        subtitle = file.subtitle(deviceName),
        subtitleColor = if (waiting) MaterialTheme.colorScheme.error else null,
        subtitleLeading = { StatusDot(color = file.status.color()) },
        leading = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
            ) {
                AnimatedVisibility(
                    visible = editing,
                    enter = expandHorizontally() + fadeIn(),
                    exit = shrinkHorizontally() + fadeOut(),
                ) {
                    Checkbox(
                        modifier = Modifier.size(24.dp),
                        checked = selected,
                        onCheckedChange = { onToggle() },
                        colors = CheckboxDefaults.colors(
                            checkedColor = MaterialTheme.colorScheme.primary,
                            uncheckedColor = MaterialTheme.colorScheme.outline,
                        ),
                    )
                }
                Box {
                    DkThumbnail(icon = file.preview.kind.icon())
                    if (file.preview.kind.isMedia) {
                        FileThumbnail(
                            modifier = Modifier
                                .matchParentSize()
                                .clip(MaterialTheme.shapes.medium),
                            file = file.preview,
                        )
                    }
                }
            }
        },
        trailing = { DkMonoCaption(text = FileSize(file.bytes).formatted()) },
        onClick = if (editing) onToggle else onOpen,
        onLongClick = if (editing) onOpen else onLongPress,
    )
}

@Composable
private fun StorageSourceState.FileUi.subtitle(deviceName: String): String {
    val status = when (status) {
        CopyStatusUi.OnPeer -> stringResource(R.string.storage_file_on_peer, deviceName)
        CopyStatusUi.Waiting -> stringResource(R.string.storage_file_waiting)
        CopyStatusUi.Sending -> stringResource(R.string.storage_file_sending)
    }
    val folderName = folder?.substringAfterLast('/') ?: return status

    return stringResource(R.string.storage_file_in_folder, folderName, status)
}

@Composable
private fun CopyStatusUi.color(): Color = when (this) {
    CopyStatusUi.OnPeer -> MaterialTheme.colorScheme.primary
    CopyStatusUi.Waiting -> MaterialTheme.colorScheme.error
    CopyStatusUi.Sending -> MaterialTheme.colorScheme.outline
}

@Composable
private fun StatusDot(modifier: Modifier = Modifier, color: Color) {
    Box(
        modifier = modifier
            .size(6.dp)
            .clip(CircleShape)
            .background(color),
    )
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun StorageFileRowPreview() {
    val files = StorageSourceState.Sample.files

    FServerTheme {
        Column {
            files.take(3).forEachIndexed { index, file ->
                StorageFileRow(
                    file = file,
                    deviceName = "Server",
                    editing = index > 0,
                    selected = index == 1,
                    onOpen = {},
                    onToggle = {},
                    onLongPress = {},
                )
            }
        }
    }
}
