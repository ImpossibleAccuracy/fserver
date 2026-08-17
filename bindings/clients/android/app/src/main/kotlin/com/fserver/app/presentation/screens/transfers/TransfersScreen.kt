package com.fserver.app.presentation.screens.transfers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.data.SampleData
import com.fserver.app.presentation.designkit.DkCard
import com.fserver.app.presentation.designkit.DkCardMeta
import com.fserver.app.presentation.designkit.DkProgressBar
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTag
import com.fserver.app.presentation.designkit.DkTagStyle
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.designkit.DkType
import com.fserver.app.presentation.composable.model.TransferUi
import com.fserver.app.presentation.screens.transfers.model.TransfersIntent
import com.fserver.app.presentation.screens.transfers.model.TransfersState
import com.fserver.app.presentation.theme.FServerTheme
import org.koin.androidx.compose.koinViewModel

@Composable
fun TransfersScreen(viewModel: TransfersViewModel = koinViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    TransfersScreen(state = state, onIntent = viewModel::onIntent)
}

/**
 * The transfer queue.
 *
 * A dropped connection is drawn as an ordinary state with a "resume" affordance, not as
 * an error: transfers resume from where they stopped and the hash is verified at the end,
 * so an interruption costs the user nothing but time.
 */
@Composable
private fun TransfersScreen(
    state: TransfersState,
    onIntent: (TransfersIntent) -> Unit,
) {
    DkScaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            DkTopBar(
                title = stringResource(R.string.transfers_title),
                actions = {
                    TextButton(onClick = { onIntent(TransfersIntent.ClearClicked) }) {
                        Text(
                            text = stringResource(R.string.action_clear),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = DkSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
        ) {
            items(state.transfers, key = { it.id }) { transfer ->
                when (transfer) {
                    is TransferUi.Running -> RunningTransferCard(
                        transfer = transfer,
                        onPause = { onIntent(TransfersIntent.PauseClicked(transfer.id)) },
                    )

                    is TransferUi.Paused -> PausedTransferCard(
                        transfer = transfer,
                        onResume = { onIntent(TransfersIntent.ResumeClicked(transfer.id)) },
                    )

                    is TransferUi.Queued -> QueuedTransferCard(transfer)

                    is TransferUi.Interrupted -> InterruptedTransferCard(
                        transfer = transfer,
                        onResume = { onIntent(TransfersIntent.ResumeClicked(transfer.id)) },
                    )

                    is TransferUi.Completed -> CompletedTransferRow(transfer)
                }
            }
        }
    }
}

@Composable
private fun RunningTransferCard(transfer: TransferUi.Running, onPause: () -> Unit) {
    DkCard {
        TransferHeader(fileName = transfer.fileName) {
            Text(
                text = stringResource(
                    R.string.transfer_percent,
                    (transfer.progress * 100).toInt(),
                ),
                style = DkType.mono,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        DkProgressBar(progress = transfer.progress)
        DkCardMeta(
            text = stringResource(
                R.string.transfer_progress_meta,
                transfer.transferredLabel,
                transfer.totalLabel,
                transfer.speedLabel,
                transfer.etaLabel,
            ),
            trailing = {
                TransferAction(
                    text = stringResource(R.string.transfer_action_pause),
                    onClick = onPause
                )
            },
        )
    }
}

@Composable
private fun PausedTransferCard(transfer: TransferUi.Paused, onResume: () -> Unit) {
    DkCard {
        TransferHeader(fileName = transfer.fileName) {
            Text(
                text = stringResource(R.string.transfer_paused),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DkProgressBar(progress = transfer.progress)
        DkCardMeta(
            text = stringResource(
                R.string.transfer_paused_meta,
                transfer.transferredLabel,
                transfer.totalLabel,
            ),
            trailing = {
                TransferAction(
                    text = stringResource(R.string.transfer_action_resume),
                    onClick = onResume,
                )
            },
        )
    }
}

@Composable
private fun QueuedTransferCard(transfer: TransferUi.Queued) {
    DkCard(modifier = Modifier.alpha(0.75f)) {
        TransferHeader(fileName = transfer.fileName) {
            DkTag(stringResource(R.string.transfer_queued), style = DkTagStyle.Neutral)
        }
    }
}

@Composable
private fun InterruptedTransferCard(transfer: TransferUi.Interrupted, onResume: () -> Unit) {
    DkCard(outlined = true) {
        TransferHeader(fileName = transfer.fileName) {
            Text(
                text = stringResource(R.string.transfer_interrupted),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DkCardMeta(
            text = stringResource(R.string.transfer_interrupted_meta, transfer.stoppedAtPercent),
            trailing = {
                TransferAction(
                    text = stringResource(R.string.transfer_action_resume),
                    onClick = onResume,
                )
            },
        )
    }
}

@Composable
private fun CompletedTransferRow(transfer: TransferUi.Completed) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .alpha(0.55f)
            .padding(vertical = DkSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
    ) {
        DkThumbnail(icon = Icons.AutoMirrored.Filled.InsertDriveFile)
        Text(
            text = transfer.fileName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = stringResource(R.string.transfer_hash_verified),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun TransferHeader(fileName: String, trailing: @Composable () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
    ) {
        DkThumbnail(icon = Icons.AutoMirrored.Filled.InsertDriveFile)
        Text(
            text = fileName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
private fun TransferAction(text: String, onClick: () -> Unit) {
    TextButton(onClick = onClick, contentPadding = PaddingValues(horizontal = DkSpacing.sm)) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun TransfersScreenPreview() {
    FServerTheme {
        TransfersScreen(
            state = TransfersState(transfers = SampleData.transfers),
            onIntent = {},
        )
    }
}
