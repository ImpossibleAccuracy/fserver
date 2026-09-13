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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.icon
import com.fserver.app.presentation.composable.model.localizedName
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkIconButton
import com.fserver.app.presentation.designkit.DkListRow
import com.fserver.app.presentation.designkit.DkMonoCaption
import com.fserver.app.presentation.designkit.DkProgressBar
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
    onFolderClick: (FilesState.FolderUi) -> Unit,
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
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DkSpacing.xs),
                ) {
                    Text(
                        text = device.name,
                        style = MaterialTheme.typography.titleMedium,
                        color = colors.onSurface,
                    )

                    Box(
                        modifier = Modifier
                            .size(StatusDot)
                            .clip(CircleShape)
                            .background(
                                if (device.online) colors.primary else colors.outline,
                            )
                    )
                }
                Row(
                    modifier = Modifier.padding(top = DkSpacing.xxs),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(DkSpacing.xs),
                ) {
                    DkCaption(
                        text = listOfNotNull(
                            stringResource(
                                if (device.online) R.string.device_state_online
                                else R.string.device_state_offline
                            ),
                            device.addressLabel,
                        ).joinToString(" · "),
                    )
                }
                val reach = listOfNotNull(
                    device.foundBy?.let {
                        stringResource(
                            R.string.files_device_found_by,
                            stringResource(it.localizedName)
                        )
                    },
                    device.lastSeenLabel?.let { stringResource(R.string.files_device_seen, it) },
                )
                if (reach.isNotEmpty()) {
                    DkCaption(
                        modifier = Modifier.padding(top = DkSpacing.xxs),
                        text = reach.joinToString(" · "),
                    )
                }
            }
            DkIconButton(onClick = onClose, icon = Icons.Default.Close)
        }

        Column(verticalArrangement = Arrangement.spacedBy(DkSpacing.sm)) {
            DkSectionLabel(text = stringResource(R.string.files_device_folders))

            device.folders.forEach { folder ->
                FolderRow(
                    folder = folder,
                    onClick = { onFolderClick(folder) },
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm)) {
            DkSecondaryButton(
                modifier = Modifier.weight(1f),
                text = stringResource(R.string.files_device_add_folder),
                onClick = {
                    onAddFolder()
                    onClose()
                },
            )
            DkGhostButton(
                text = stringResource(R.string.files_device_configure),
                onClick = {
                    onConfigure()
                    onClose()
                },
            )
        }

        device.fingerprintLabel?.let { fingerprint ->
            DkMonoCaption(text = stringResource(R.string.files_device_fingerprint, fingerprint))
        }
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

    Column(
        modifier = modifier
            .clip(shape)
            .border(
                width = 1.dp,
                color = if (folder.accented) colors.primary else colors.outlineVariant,
                shape = shape,
            ),
    ) {
        DkListRow(
            title = folder.name,
            subtitle = listOfNotNull(
                stringResource(folder.mode.titleRes),
                folder.statusText(),
                pluralStringResource(
                    R.plurals.files_folder_items,
                    folder.itemCount,
                    folder.itemCount,
                ).takeIf { folder.itemCount > 0 },
            ).joinToString(" · "),
            subtitleMaxLines = 2,
            onClick = onClick,
            trailing = {
                if (folder.path != null) {
                    DkIcon(icon = Icons.AutoMirrored.Filled.KeyboardArrowRight)
                }
            },
            contentPaddings = PaddingValues(horizontal = DkSpacing.md, vertical = DkSpacing.md),
        )

        if (folder.status == FilesState.FolderStatusUi.Syncing) {
            DkProgressBar(
                modifier = Modifier.padding(
                    start = DkSpacing.md,
                    end = DkSpacing.md,
                    bottom = DkSpacing.md,
                ),
                progress = folder.progress,
            )
        }
    }
}

@Composable
private fun FilesState.FolderUi.statusText(): String = when (status) {
    FilesState.FolderStatusUi.Pending -> stringResource(R.string.files_folder_status_pending)
    FilesState.FolderStatusUi.Syncing -> stringResource(R.string.files_folder_status_syncing)

    FilesState.FolderStatusUi.Disabled -> stringResource(
        R.string.files_folder_status_disabled,
        statusDetail.orEmpty(),
    )

    FilesState.FolderStatusUi.Active -> statusDetail
        ?.let { stringResource(R.string.files_folder_status_synced, it) }
        ?: stringResource(R.string.files_folder_status_never)
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
