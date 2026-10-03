package com.fserver.app.presentation.screens.activity

import com.fserver.core.crypto.EncryptionProgress
import androidx.compose.material.icons.filled.Lock
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.fserver.app.R
import com.fserver.common.model.FileSize
import com.fserver.app.presentation.composable.model.TransferUi
import com.fserver.app.presentation.composable.model.etaFormatted
import com.fserver.app.presentation.composable.model.formatted
import com.fserver.app.presentation.composable.model.icon
import com.fserver.app.presentation.composable.model.labelRes
import com.fserver.app.presentation.composable.model.rateFormatted
import com.fserver.app.presentation.designkit.DkCaption
import com.fserver.app.presentation.designkit.DkCard
import com.fserver.app.presentation.designkit.DkCardMeta
import com.fserver.app.presentation.designkit.DkGhostButton
import com.fserver.app.presentation.designkit.DkIconButton
import com.fserver.app.presentation.designkit.DkProgressBar
import com.fserver.app.presentation.designkit.DkScaffold
import com.fserver.app.presentation.designkit.DkSecondaryButton
import com.fserver.app.presentation.designkit.DkSectionLabel
import com.fserver.app.presentation.designkit.DkSpacing
import com.fserver.app.presentation.designkit.DkTag
import com.fserver.app.presentation.designkit.DkTagStyle
import com.fserver.app.presentation.designkit.DkThumbnail
import com.fserver.app.presentation.designkit.DkTopBar
import com.fserver.app.presentation.designkit.DkType
import com.fserver.app.presentation.screens.activity.model.ActivityIntent
import com.fserver.app.presentation.screens.activity.model.ActivityState
import com.fserver.app.presentation.screens.source.request.shared.composable.SyncRequestBanner
import com.fserver.app.presentation.theme.FServerTheme
import com.fserver.app.presentation.shared.journal.composable.JournalEntryRow
import com.fserver.app.presentation.shared.journal.model.JournalEntryUi
import com.fserver.core.sync.progress.FileTransfer
import org.koin.androidx.compose.koinViewModel

@Composable
fun ActivityScreen(
    modifier: Modifier = Modifier,
    viewModel: ActivityViewModel = koinViewModel(),
    navigateToSyncRequests: () -> Unit,
    navigateToHistory: () -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    ActivityScreenContent(
        modifier = modifier,
        state = state,
        onIntent = viewModel::onIntent,
        navigateToSyncRequests = navigateToSyncRequests,
        navigateToHistory = navigateToHistory,
    )
}

@Composable
private fun ActivityScreenContent(
    modifier: Modifier = Modifier,
    state: ActivityState,
    onIntent: (ActivityIntent) -> Unit,
    navigateToSyncRequests: () -> Unit,
    navigateToHistory: () -> Unit,
) {
    DkScaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            DkTopBar(title = stringResource(R.string.activity_title))
        },
    ) { innerPadding ->
        if (state.isEmpty && !state.hasTotals) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = DkSpacing.screenPadding),
                contentAlignment = Alignment.Center,
            ) {
                DkCaption(
                    text = stringResource(R.string.activity_empty),
                    textAlign = TextAlign.Center,
                )
            }
            return@DkScaffold
        }

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(horizontal = DkSpacing.screenPadding),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        ) {
            if (state.hasTotals) {
                item(key = "totals") {
                    TotalsRow(freedLabel = state.freedLabel, quotaLabel = state.quotaLabel)
                }
            }

            if (state.needsAttention) {
                item(key = "attention-label") {
                    DkSectionLabel(text = stringResource(R.string.activity_section_attention))
                }
                state.syncRequest?.let { request ->
                    item(key = "attention-requests") {
                        SyncRequestBanner(
                            request = request,
                            waiting = state.syncRequestsWaiting,
                            onClick = navigateToSyncRequests,
                        )
                    }
                }
                items(state.conflicts, key = { it.id }) { conflict ->
                    ConflictCard(conflict = conflict, onIntent = onIntent)
                }
                items(state.issues, key = { "issue-${it.id}" }) { issue ->
                    IssueCard(
                        issue = issue,
                        onDismiss = { onIntent(ActivityIntent.DismissClicked(issue.id)) },
                    )
                }
            }

            if (state.isMoving) {
                item(key = "now-label") {
                    DkSectionLabel(text = stringResource(R.string.activity_section_now))
                }
                state.encryption?.let { encryption ->
                    item(key = "encryption") {
                        EncryptionCard(encryption = encryption)
                    }
                }
                items(state.running, key = { it.id }) { transfer ->
                    when (transfer) {
                        is TransferUi.Batch -> BatchCard(transfer)
                        is TransferUi.Running -> RunningCard(transfer)
                        is TransferUi.Interrupted,
                        is TransferUi.Completed,
                            -> Unit
                    }
                }
                state.queued?.let { queued ->
                    item(key = "queued") {
                        QueuedCard(queued = queued)
                    }
                }
                items(state.interrupted, key = { it.id }) { transfer ->
                    InterruptedCard(
                        transfer = transfer,
                        onRetry = { onIntent(ActivityIntent.RetryClicked(transfer.id)) },
                    )
                }
            }

            if (state.history.isNotEmpty()) {
                item(key = "history-label") {
                    DkSectionLabel(text = stringResource(R.string.activity_section_today))
                }
                items(state.history, key = { it.entry.id }) { item ->
                    HistoryRow(
                        item = item,
                        onUndo = { onIntent(ActivityIntent.UndoClicked(item.entry.id)) },
                    )
                }
                item(key = "history-all") {
                    DkSecondaryButton(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = DkSpacing.sm),
                        text = stringResource(R.string.activity_full_history),
                        onClick = navigateToHistory,
                    )
                }
            }
        }
    }
}

@Composable
private fun TotalsRow(
    modifier: Modifier = Modifier,
    freedLabel: String?,
    quotaLabel: String?,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
    ) {
        if (freedLabel != null) {
            TotalCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.activity_total_freed),
                value = freedLabel,
            )
        }
        if (quotaLabel != null) {
            TotalCard(
                modifier = Modifier.weight(1f),
                label = stringResource(R.string.activity_total_quota),
                value = quotaLabel,
            )
        }
    }
}

@Composable
private fun TotalCard(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
) {
    DkCard(modifier = modifier) {
        DkCaption(text = label)
        Text(
            text = value,
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConflictCard(
    modifier: Modifier = Modifier,
    conflict: ActivityState.ConflictUi,
    onIntent: (ActivityIntent) -> Unit,
) {
    DkCard(modifier = modifier, outlined = true) {
        Row(horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm)) {
            Icon(
                modifier = Modifier
                    .padding(top = DkSpacing.xxs)
                    .size(16.dp),
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.activity_conflict_title, conflict.fileName),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                DkCaption(
                    modifier = Modifier.padding(top = DkSpacing.xxs),
                    text = stringResource(conflict.change.detailRes, conflict.peerName),
                )
            }
        }
        // TODO: compare screen with previews of both versions; the peer's one fetched on demand into a cache, not the source.
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
            verticalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        ) {
            if (conflict.canKeepMine) {
                DkSecondaryButton(
                    text = stringResource(R.string.activity_conflict_keep_mine),
                    onClick = { onIntent(ActivityIntent.ConflictKeepMineClicked(conflict.id)) },
                )
            }
            if (conflict.canKeepTheirs) {
                DkSecondaryButton(
                    text = stringResource(R.string.activity_conflict_keep_theirs, conflict.peerName),
                    onClick = { onIntent(ActivityIntent.ConflictKeepTheirsClicked(conflict.id)) },
                )
            }
            if (conflict.canKeepBoth) {
                DkGhostButton(
                    text = stringResource(R.string.activity_conflict_keep_both),
                    onClick = { onIntent(ActivityIntent.ConflictKeepBothClicked(conflict.id)) },
                )
            }
        }
    }
}

private val ActivityState.ChangeUi.detailRes: Int
    get() = when (this) {
        ActivityState.ChangeUi.EditedBoth -> R.string.activity_conflict_edited_both
        ActivityState.ChangeUi.DeletedHere -> R.string.activity_conflict_deleted_here
        ActivityState.ChangeUi.DeletedThere -> R.string.activity_conflict_deleted_there
    }

@Composable
private fun BatchCard(transfer: TransferUi.Batch, modifier: Modifier = Modifier) {
    DkCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        ) {
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(
                    when (transfer.direction) {
                        FileTransfer.Direction.Outgoing -> R.string.activity_batch_outgoing
                        FileTransfer.Direction.Incoming -> R.string.activity_batch_incoming
                    },
                    transfer.peerName,
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = stringResource(
                    R.string.activity_batch_counter,
                    transfer.doneCount,
                    transfer.totalCount,
                ),
                style = DkType.mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DkProgressBar(progress = transfer.progress)
        DkCardMeta(
            text = stringResource(
                R.string.value_with_detail,
                transfer.fileName,
                rateFormatted(transfer.bytesPerSecond),
            ),
        )
    }
}

@Composable
private fun RunningCard(transfer: TransferUi.Running, modifier: Modifier = Modifier) {
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
private fun EncryptionCard(modifier: Modifier = Modifier, encryption: ActivityState.EncryptionUi) {
    DkCard(modifier = modifier) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        ) {
            DkThumbnail(icon = Icons.Default.Lock)
            Text(
                modifier = Modifier.weight(1f),
                text = stringResource(encryption.titleRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = stringResource(R.string.activity_batch_counter, encryption.done, encryption.total),
                style = DkType.mono,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DkProgressBar(progress = encryption.progress)
    }
}

private val ActivityState.EncryptionUi.titleRes: Int
    get() = when (towards) {
        setOf(EncryptionProgress.Toward.Encrypted) -> R.string.activity_encryption_encrypting
        setOf(EncryptionProgress.Toward.Decrypted) -> R.string.activity_encryption_decrypting
        else -> R.string.activity_encryption_updating
    }

@Composable
private fun QueuedCard(modifier: Modifier = Modifier, queued: ActivityState.QueuedUi) {
    DkCard(modifier = modifier.alpha(0.75f)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        ) {
            DkThumbnail(icon = Icons.Default.HourglassEmpty)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = pluralStringResource(R.plurals.activity_queued_files, queued.count, queued.count),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                DkCaption(
                    text = listOfNotNull(
                        stringResource(R.string.journal_tally_sent, queued.outgoing).takeIf { queued.outgoing > 0 },
                        stringResource(R.string.journal_tally_received, queued.incoming).takeIf { queued.incoming > 0 },
                        FileSize(queued.bytes).formatted().takeIf { queued.bytes > 0 },
                    ).joinToString(" · "),
                )
            }
            DkTag(stringResource(R.string.transfer_queued), style = DkTagStyle.Neutral)
        }
    }
}

@Composable
private fun InterruptedCard(
    modifier: Modifier = Modifier,
    transfer: TransferUi.Interrupted,
    onRetry: () -> Unit,
) {
    DkCard(modifier = modifier, outlined = true) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(DkSpacing.sm),
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = transfer.fileName,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    modifier = Modifier.padding(top = DkSpacing.xxs),
                    text = stringResource(
                        R.string.activity_interrupted_meta,
                        transfer.stoppedAtPercent,
                    ),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.error,
                )
            }
            DkIconButton(onClick = onRetry, icon = Icons.Default.Refresh)
        }
    }
}

@Composable
private fun IssueCard(
    modifier: Modifier = Modifier,
    issue: JournalEntryUi,
    onDismiss: () -> Unit,
) {
    DkCard(modifier = modifier, outlined = true) {
        JournalEntryRow(
            entry = issue,
            onDismiss = onDismiss,
            contentPaddings = PaddingValues(),
        )
    }
}

@Composable
private fun HistoryRow(
    modifier: Modifier = Modifier,
    item: ActivityState.HistoryUi,
    onUndo: () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        JournalEntryRow(
            entry = item.entry,
            contentPaddings = PaddingValues(vertical = DkSpacing.xs),
        )
        if (item.undoable) {
            DkGhostButton(
                text = stringResource(R.string.action_undo),
                onClick = onUndo,
            )
        }
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
            modifier = Modifier.weight(1f),
            text = fileName,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        trailing()
    }
}

@Preview(showBackground = true, widthDp = 360, heightDp = 900)
@Composable
private fun ActivityScreenPreview() {
    FServerTheme {
        ActivityScreenContent(
            state = ActivityState(
                freedLabel = "12.4 GB",
                quotaLabel = "61 %",
                syncRequestsWaiting = 3,
                conflicts = ActivityState.SampleConflicts,
                running = ActivityState.SampleRunning,
                queued = ActivityState.SampleQueued,
                encryption = ActivityState.SampleEncryption,
                interrupted = ActivityState.SampleInterrupted,
                issues = ActivityState.SampleIssues,
                history = ActivityState.SampleHistory,
            ),
            onIntent = {},
            navigateToSyncRequests = {},
            navigateToHistory = {},
        )
    }
}

@Preview(name = "Empty", showBackground = true, widthDp = 360, heightDp = 720)
@Composable
private fun ActivityScreenEmptyPreview() {
    FServerTheme {
        ActivityScreenContent(
            state = ActivityState(),
            onIntent = {},
            navigateToSyncRequests = {},
            navigateToHistory = {},
        )
    }
}
