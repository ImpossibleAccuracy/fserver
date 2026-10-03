package com.fserver.app.presentation.screens.settings.transfers

import android.text.format.DateUtils
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.domain.oneshot.OneShotDestinations
import com.fserver.app.presentation.composable.model.fileExtension
import com.fserver.app.presentation.composable.model.fileKindOf
import com.fserver.app.presentation.composable.model.formatted
import com.fserver.app.presentation.designkit.DkFadingDivider
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkIcon
import com.fserver.app.presentation.designkit.DkProgressBar
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSecondaryButton
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSettingsRow
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkSwitchRow
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.screens.settings.transfers.model.TransfersIntent
import com.fserver.app.presentation.screens.settings.transfers.model.TransfersState
import com.fserver.app.presentation.shared.browser.composable.BrowserFileRow
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.shared.oneshot.DestinationPickerSheet
import com.fserver.app.presentation.shared.oneshot.destinationLabel
import com.fserver.app.presentation.shared.viewer.LocalFileOpener
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Clock

@Composable
fun TransfersScreen(
    viewModel: TransfersViewModel = koinViewModel(),
    navigateUp: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var pickingDestination by remember { mutableStateOf(false) }

    TransfersScreen(
        state = state,
        onIntent = viewModel::onIntent,
        onChangeDestination = { pickingDestination = true },
        navigateUp = navigateUp,
    )

    val destination = state.destination
    if (pickingDestination && destination != null) {
        DestinationPickerSheet(
            current = destination,
            onPick = {
                viewModel.onIntent(TransfersIntent.DestinationPicked(it))
                pickingDestination = false
            },
            onError = { viewModel.reportError(it, "could not take a folder grant") },
            onDismiss = { pickingDestination = false },
        )
    }
}

/** Receiving preferences above, every one-shot transfer below. */
@Composable
private fun TransfersScreen(
    state: TransfersState,
    onIntent: (TransfersIntent) -> Unit,
    onChangeDestination: () -> Unit,
    navigateUp: () -> Unit,
) {
    DkScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DkTopBar(title = stringResource(R.string.transfers_title), onBack = navigateUp)
        },
    ) { innerPadding ->
        val gutter = Modifier.padding(horizontal = DkSpacing.screenPadding)

        LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = innerPadding) {
            item {
                DkSectionLabel(modifier = gutter, text = stringResource(R.string.transfers_section_receiving))

                DkSettingsRow(
                    title = stringResource(R.string.transfers_destination),
                    supportingText = state.destination?.let {
                        stringResource(R.string.incoming_save_to, it.destinationLabel())
                    },
                    trailing = {
                        DkGhostButton(text = stringResource(R.string.action_edit), onClick = onChangeDestination)
                    },
                )
                DkFadingDivider()

                DkSwitchRow(
                    title = stringResource(R.string.transfers_auto_accept),
                    supportingText = stringResource(R.string.transfers_auto_accept_desc),
                    checked = state.autoAccept,
                    onCheckedChange = { onIntent(TransfersIntent.AutoAcceptChanged(it)) },
                )

                DkSectionLabel(modifier = gutter, text = stringResource(R.string.transfers_section_history))

                if (state.hasFinished) {
                    DkSecondaryButton(
                        modifier = gutter
                            .fillMaxWidth()
                            .padding(bottom = DkSpacing.sm),
                        text = stringResource(R.string.transfers_clear_finished),
                        onClick = { onIntent(TransfersIntent.ClearFinishedClicked) },
                    )
                }
            }

            if (state.transfers.isEmpty()) {
                item {
                    Text(
                        modifier = gutter,
                        text = stringResource(R.string.transfers_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            items(state.transfers, key = { it.id }) { transfer ->
                TransferItem(transfer = transfer, onIntent = onIntent)
                DkFadingDivider()
            }
        }
    }
}

/** Who and how far, then each file as the file browser draws it, then what can still be done. */
@Composable
private fun TransferItem(
    transfer: TransfersState.TransferUi,
    onIntent: (TransfersIntent) -> Unit,
) {
    val opener = LocalFileOpener.current
    val gutter = Modifier.padding(horizontal = DkSpacing.screenPadding)

    Column(modifier = Modifier.padding(top = DkSpacing.md)) {
        Row(
            modifier = gutter,
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        ) {
            DkIcon(icon = if (transfer.outgoing) Icons.Default.Upload else Icons.Default.Download)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(
                        if (transfer.outgoing) R.string.transfers_to else R.string.transfers_from,
                        transfer.peerName,
                    ),
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    text = stringResource(
                        R.string.transfers_meta,
                        pluralStringResource(R.plurals.transfers_files, transfer.files.size, transfer.files.size),
                        transfer.totalSize.formatted(),
                        transfer.status.label(transfer.outgoing),
                        DateUtils.getRelativeTimeSpanString(
                            transfer.createdAt.toEpochMilliseconds(),
                            Clock.System.now().toEpochMilliseconds(),
                            DateUtils.MINUTE_IN_MILLIS,
                        ),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        transfer.progress?.let {
            DkProgressBar(modifier = gutter.padding(top = DkSpacing.sm), progress = it)
        }

        transfer.files.forEach { file ->
            BrowserFileRow(
                file = file.toBrowserFile(),
                selection = null,
                onFileClick = { if (file.openLocator != null) opener.open(it) },
                onFileLongClick = null,
            )
        }

        Row(
            modifier = gutter.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(DkSpacing.xs, Alignment.End),
        ) {
            if (transfer.canRetry) {
                DkGhostButton(
                    text = stringResource(R.string.action_retry),
                    onClick = { onIntent(TransfersIntent.RetryClicked(transfer.id)) },
                )
            }
            if (transfer.canCancel) {
                DkGhostButton(
                    text = stringResource(R.string.action_cancel),
                    danger = true,
                    onClick = { onIntent(TransfersIntent.CancelClicked(transfer.id)) },
                )
            } else {
                DkGhostButton(
                    text = stringResource(R.string.action_delete),
                    onClick = { onIntent(TransfersIntent.DeleteClicked(transfer.id)) },
                )
            }
        }
    }
}

@Composable
private fun TransfersState.StatusUi.label(outgoing: Boolean): String = when (this) {
    TransfersState.StatusUi.Pending -> stringResource(
        if (outgoing) R.string.transfers_status_waiting_peer else R.string.transfers_status_waiting_you,
    )
    TransfersState.StatusUi.Active -> stringResource(R.string.transfers_status_active)
    TransfersState.StatusUi.Completed -> stringResource(R.string.transfers_status_completed)
    TransfersState.StatusUi.Declined -> stringResource(R.string.transfers_status_declined)
    TransfersState.StatusUi.Cancelled -> stringResource(R.string.transfers_status_cancelled)
    is TransfersState.StatusUi.Failed -> stringResource(R.string.transfers_status_failed, reason)
}

// Only a received file carries a locator: the thumbnail and the viewer both read it.
private fun TransfersState.FileUi.toBrowserFile() = FileBrowserUi.File(
    path = name,
    name = name,
    kind = fileKindOf(name),
    locator = openLocator,
    size = size,
    extensionLabel = name.fileExtension.uppercase().ifEmpty { null },
)

@Preview(showBackground = true, heightDp = 800)
@Composable
private fun TransfersScreenPreview() {
    FServerTheme {
        TransfersScreen(
            state = TransfersState(
                destination = OneShotDestinations.Downloads,
                transfers = listOf(
                    TransfersState.TransferUi(
                        id = "1",
                        peerName = "Laptop",
                        outgoing = true,
                        status = TransfersState.StatusUi.Active,
                        files = listOf(
                            TransfersState.FileUi(0, "IMG_4831.jpg", FileSize(4_200_000), null),
                            TransfersState.FileUi(1, "notes.txt", FileSize(1_200), null),
                        ),
                        totalSize = FileSize(4_201_200),
                        progress = 0.4f,
                        createdAt = Clock.System.now(),
                    ),
                    TransfersState.TransferUi(
                        id = "2",
                        peerName = "Home PC",
                        outgoing = false,
                        status = TransfersState.StatusUi.Completed,
                        files = listOf(TransfersState.FileUi(0, "report.pdf", FileSize(820_000), "/x")),
                        totalSize = FileSize(820_000),
                        progress = null,
                        createdAt = Clock.System.now(),
                    ),
                ),
            ),
            onIntent = {},
            onChangeDestination = {},
            navigateUp = {},
        )
    }
}
