package com.fserver.app.presentation.screens.transfers

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.TransferUi
import com.fserver.app.presentation.composable.model.etaFormatted
import com.fserver.app.presentation.composable.model.formatted
import com.fserver.app.presentation.composable.model.icon
import com.fserver.app.presentation.composable.model.labelRes
import com.fserver.app.presentation.composable.model.rateFormatted
import com.fserver.app.presentation.designkit.DkCaption
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
import com.fserver.app.presentation.screens.transfers.model.TransfersIntent
import com.fserver.app.presentation.screens.transfers.model.TransfersState
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.common.model.FileSize
import com.fserver.core.sync.progress.FileTransfer
import org.koin.androidx.compose.koinViewModel
import kotlin.time.Duration.Companion.seconds

@Composable
fun TransfersScreen(
    modifier: Modifier = Modifier,
    viewModel: TransfersViewModel = koinViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    TransfersScreenContent(
        modifier = modifier,
        state = state,
        onIntent = viewModel::onIntent,
    )
}

@Composable
private fun TransfersScreenContent(
    state: TransfersState,
    onIntent: (TransfersIntent) -> Unit,
    modifier: Modifier = Modifier,
) {
    DkScaffold(
        modifier = modifier.fillMaxSize(),
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
        if (state.isEmpty) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = DkSpacing.screenPadding),
                contentAlignment = Alignment.Center,
            ) {
                DkCaption(
                    text = stringResource(R.string.transfers_empty),
                    textAlign = TextAlign.Center,
                )
            }
            return@DkScaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = DkSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.md),
        ) {
            items(state.transfers, key = { it.id }) { transfer ->
                when (transfer) {
                    is TransferUi.Running -> RunningTransferCard(transfer)

                    is TransferUi.Queued -> QueuedTransferCard(transfer)

                    is TransferUi.Interrupted -> InterruptedTransferCard(
                        transfer = transfer,
                        onRetry = { onIntent(TransfersIntent.RetryClicked) },
                    )

                    is TransferUi.Completed -> CompletedTransferRow(transfer)
                }
            }
        }
    }
}

@Composable
private fun RunningTransferCard(transfer: TransferUi.Running, modifier: Modifier = Modifier) {
    DkCard(modifier = modifier) {
        TransferHeader(fileName = transfer.fileName, direction = transfer.direction) {
            Text(
                text = stringResource(
                    R.string.transfer_percent,
                    ((transfer.progress ?: 0f) * 100).toInt(),
                ),
                style = DkType.mono,
                color = MaterialTheme.colorScheme.tertiary,
            )
        }
        DkProgressBar(progress = transfer.progress)
        DkCardMeta(
            text = stringResource(
                R.string.transfer_progress_meta,
                transfer.transferred.formatted(),
                transfer.total.formatted(),
                rateFormatted(transfer.bytesPerSecond),
                transfer.eta.etaFormatted(),
            ),
        )
    }
}

@Composable
private fun QueuedTransferCard(transfer: TransferUi.Queued, modifier: Modifier = Modifier) {
    DkCard(modifier = modifier.alpha(0.75f)) {
        TransferHeader(fileName = transfer.fileName, direction = transfer.direction) {
            DkTag(stringResource(R.string.transfer_queued), style = DkTagStyle.Neutral)
        }
    }
}

@Composable
private fun InterruptedTransferCard(
    transfer: TransferUi.Interrupted,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    DkCard(modifier = modifier, outlined = true) {
        TransferHeader(fileName = transfer.fileName, direction = transfer.direction) {
            Text(
                text = stringResource(R.string.transfer_interrupted),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DkCardMeta(
            text = stringResource(R.string.transfer_interrupted_meta, transfer.stoppedAtPercent),
            trailing = {
                TextButton(
                    onClick = onRetry,
                    contentPadding = PaddingValues(horizontal = DkSpacing.sm),
                ) {
                    Text(
                        text = stringResource(R.string.transfer_action_resume),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            },
        )
    }
}

@Composable
private fun CompletedTransferRow(transfer: TransferUi.Completed, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .alpha(0.55f)
            .padding(vertical = DkSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
    ) {
        DkThumbnail(
            icon = transfer.direction.icon,
            contentDescription = stringResource(transfer.direction.labelRes),
        )
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
private fun TransferHeader(
    fileName: String,
    direction: FileTransfer.Direction,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
    ) {
        DkThumbnail(
            icon = direction.icon,
            contentDescription = stringResource(direction.labelRes),
        )
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

@Preview(showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun TransfersScreenPreview() {
    FServerTheme {
        TransfersScreenContent(
            state = TransfersState(
                transfers = listOf(
                    TransferUi.Running(
                        id = "Outgoing/src/clip",
                        fileName = "clip_final.mp4",
                        direction = FileTransfer.Direction.Outgoing,
                        progress = 0.62f,
                        transferred = FileSize(1_181_116_006),
                        total = FileSize(1_932_735_283),
                        bytesPerSecond = 43_000_000,
                        eta = 18.seconds,
                    ),
                    TransferUi.Queued(
                        id = "Outgoing/src/interview",
                        fileName = "interview_02.wav",
                        direction = FileTransfer.Direction.Outgoing,
                    ),
                    TransferUi.Interrupted(
                        id = "Incoming/src/raw",
                        fileName = "IMG_4830.RAW",
                        direction = FileTransfer.Direction.Incoming,
                        stoppedAtPercent = 74,
                    ),
                    TransferUi.Completed(
                        id = "Incoming/src/estimate",
                        fileName = "estimate_final.pdf",
                        direction = FileTransfer.Direction.Incoming,
                    ),
                ),
            ),
            onIntent = {},
        )
    }
}

@Preview(name = "Empty", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun TransfersScreenEmptyPreview() {
    FServerTheme {
        TransfersScreenContent(state = TransfersState(), onIntent = {})
    }
}
