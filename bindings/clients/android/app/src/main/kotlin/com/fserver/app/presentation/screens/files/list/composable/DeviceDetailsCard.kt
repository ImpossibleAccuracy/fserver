package com.fserver.app.presentation.screens.files.list.composable

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.icon
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkIconButton
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkSecondaryButton
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.screens.files.list.model.FilesState
import com.fserver.app.presentation.screens.source.shared.model.titleRes
import com.fserver.app.presentation.theme.FServerTheme

private val StatusDot = 5.dp

/**
 * A device opened where its card sits, over the dimmed feed.
 *
 * Everything the user can decide about one device is here — its folders, their modes, its key —
 * so managing a device never costs a trip to a settings screen.
 */
@Composable
fun DeviceDetailsCard(
    modifier: Modifier = Modifier,
    device: FilesState.DeviceDetailsUi,
    onClose: () -> Unit,
    onFolderClick: (String) -> Unit,
    onAddFolder: () -> Unit,
    onConfigure: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.medium

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.surfaceContainer)
            .border(1.dp, colors.primary, shape)
            .padding(DkSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(DkSpacing.md)) {
            Icon(
                modifier = Modifier
                    .padding(top = DkSpacing.xxs)
                    .size(18.dp),
                imageVector = device.kind.icon,
                contentDescription = null,
                tint = colors.primary,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = device.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = colors.onSurface,
                )
                Row(
                    modifier = Modifier.padding(top = DkSpacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DkSpacing.xs),
                ) {
                    Box(
                        modifier = Modifier
                            .size(StatusDot)
                            .background(
                                if (device.online) colors.primary else colors.outline,
                                CircleShape,
                            )
                    )
                    DkCaption(
                        text = listOfNotNull(
                            stringResource(
                                if (device.online) {
                                    R.string.device_state_online
                                } else {
                                    R.string.device_state_offline
                                }
                            ),
                            device.addressLabel,
                        ).joinToString(" · "),
                    )
                }
            }
            DkIconButton(onClick = onClose, icon = Icons.Default.Close)
        }

        DkSectionLabel(text = stringResource(R.string.files_device_folders))

        Column(verticalArrangement = Arrangement.spacedBy(DkSpacing.sm)) {
            device.folders.forEach { folder ->
                FolderRow(folder = folder, onClick = { onFolderClick(folder.id) })
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm)) {
            DkSecondaryButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.files_device_add_folder),
                onClick = onAddFolder,
            )
            DkGhostButton(
                text = stringResource(R.string.files_device_configure),
                onClick = onConfigure,
            )
        }

        DkMonoCaption(
            text = stringResource(R.string.files_device_fingerprint, device.fingerprintLabel),
        )
    }
}

@Composable
private fun FolderRow(
    modifier: Modifier = Modifier,
    folder: FilesState.FolderUi,
    onClick: () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = MaterialTheme.shapes.small

    DkListRow(
        modifier = modifier
            .clip(shape)
            .border(
                width = 1.dp,
                color = if (folder.accented) colors.primary else colors.outlineVariant,
                shape = shape,
            ),
        title = folder.name,
        subtitle = "${stringResource(folder.mode.titleRes)} · ${folder.detail}",
        onClick = onClick,
        trailing = { DkIcon(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight) },
        contentPaddings = PaddingValues(horizontal = DkSpacing.md, vertical = DkSpacing.md),
    )
}

@Preview(showBackground = true, widthDp = 360)
@Composable
private fun DeviceDetailsCardPreview() {
    FServerTheme {
        DeviceDetailsCard(
            modifier = Modifier.padding(DkSpacing.lg),
            device = FilesState.sampleDetailsOf(FilesState.SampleDevices[1]),
            onClose = {},
            onFolderClick = {},
            onAddFolder = {},
            onConfigure = {},
        )
    }
}
