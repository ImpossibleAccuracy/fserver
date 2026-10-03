package com.fserver.app.presentation.screens.activity

import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.flow.transformLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.delay
import kotlinx.coroutines.ExperimentalCoroutinesApi
import com.fserver.core.crypto.EncryptionController
import com.fserver.app.util.combineMany
import com.fserver.app.util.stateInScreen
import com.fserver.core.network.device.DevicesRepository
import com.fserver.app.presentation.shared.sync.SyncTrigger
import com.fserver.app.presentation.composable.model.fileName
import com.fserver.app.presentation.composable.model.peers
import com.fserver.app.presentation.composable.model.peerOf
import com.fserver.app.presentation.composable.model.PeerUi
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.composable.model.TransferUi
import com.fserver.app.presentation.screens.activity.model.ActivityIntent
import com.fserver.app.presentation.screens.activity.model.ActivityState
import com.fserver.app.presentation.screens.source.request.shared.model.toUi
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.common.model.FileSize
import com.fserver.core.storage.TrustedDevicesRepository
import com.fserver.core.sync.SourcesController
import com.fserver.core.sync.conflict.ConflictDecision
import com.fserver.core.sync.conflict.ConflictsController
import com.fserver.core.sync.conflict.FileConflict
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.progress.FileTransfer
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import com.fserver.app.presentation.composable.model.localDate
import com.fserver.app.presentation.shared.journal.journalFeed
import com.fserver.app.presentation.shared.journal.model.JournalEntryUi
import com.fserver.app.presentation.shared.journal.model.JournalKindUi
import com.fserver.core.journal.ActivityJournal
import com.fserver.core.storage.RegisteredSourcesRepository
import kotlinx.coroutines.launch

@OptIn(ExperimentalCoroutinesApi::class)
class ActivityViewModel(
    private val sourcesController: SourcesController,
    private val conflictsController: ConflictsController,
    private val trustedDevices: TrustedDevicesRepository,
    private val journal: ActivityJournal,
    registeredSources: RegisteredSourcesRepository,
    devicesRepository: DevicesRepository,
    encryptionController: EncryptionController,
    private val reporter: ErrorReporter,
) : ViewModel() {

    private val syncTrigger = SyncTrigger(viewModelScope, sourcesController, reporter)

    private val journalState = journal.journalFeed(devicesRepository, registeredSources)
        .map { entries ->
        val today = LocalDate.now()

        JournalState(
            issues = entries.filter { it.isOpenIssue && it.kind != JournalKindUi.Conflict },
            history = entries
                .filter { !it.isOpenIssue && it.at.localDate() == today }
                .map { ActivityState.HistoryUi(it) },
        )
    }

    private val now = sourcesController.progress.transfers
        .map { transfers ->
            Now(
                running = transfers.active().map { it.toRunningUi() },
                queued = transfers.queued(),
                interrupted = transfers
                    .filter { it.state is FileTransfer.State.Failed }
                    .map { it.toInterruptedUi() },
            )
        }
        .distinctUntilChanged()
        .holdingCards()

    val state: StateFlow<ActivityState> = combineMany(
        now,
        sourcesController.incomingRequests,
        devicesRepository.peers(),
        trustedDevices.devices,
        conflictsController.pending,
        journalState,
        encryptionController.progress,
    ) { now, requests, peers, trusted, conflicts, journal, encryption ->
        ActivityState(
            freedLabel = SampleFreed,
            quotaLabel = SampleQuota,
            syncRequest = requests.maxByOrNull { it.receivedAt }?.toUi(peers, trusted),
            syncRequestsWaiting = requests.size,
            conflicts = conflicts.map { it.toUi(peers) },
            issues = journal.issues,
            encryption = encryption?.let {
                ActivityState.EncryptionUi(done = it.done, total = it.total, towards = it.towards)
            },
            running = now.running,
            queued = now.queued,
            interrupted = now.interrupted,
            history = journal.history,
        )
    }.stateInScreen(
        viewModelScope,
        ActivityState(
            freedLabel = SampleFreed,
            quotaLabel = SampleQuota,
        ),
    )

    fun onIntent(intent: ActivityIntent) {
        when (intent) {
            is ActivityIntent.RetryClicked -> syncTrigger.run("Retry pass failed")
            is ActivityIntent.ConflictKeepMineClicked -> resolve(intent.conflictId, ConflictDecision.Choice.KeepLocal)
            is ActivityIntent.ConflictKeepTheirsClicked -> resolve(intent.conflictId, ConflictDecision.Choice.KeepRemote)
            is ActivityIntent.ConflictKeepBothClicked -> resolve(intent.conflictId, ConflictDecision.Choice.KeepBoth)
            is ActivityIntent.UndoClicked -> Unit
            is ActivityIntent.DismissClicked -> dismiss(intent.entryId)
        }
    }

    private fun dismiss(entryId: Long) {
        viewModelScope.launch {
            journal.solve(entryId).onFailure { reporter.report(it, "Could not dismiss journal entry $entryId") }
        }
    }

    private fun resolve(conflictId: String, choice: ConflictDecision.Choice) {
        val conflict = conflictsController.pending.value.firstOrNull { it.uiId == conflictId } ?: return

        viewModelScope.launch {
            conflictsController.resolve(conflict, choice)
                .exceptionOrNull()
                ?.let { reporter.report(it, "Could not resolve conflict on ${conflict.path}") }
        }
    }

    private fun FileConflict.toUi(peers: Map<String, PeerUi>) = ActivityState.ConflictUi(
        id = uiId,
        fileName = path.fileName(),
        peerName = peers.peerOf(remote.deviceId).name,
        change = when {
            local.state is LocalIndexedFile.State.Deleted -> ActivityState.ChangeUi.DeletedHere
            remote.state is LocalIndexedFile.State.Deleted -> ActivityState.ChangeUi.DeletedThere
            else -> ActivityState.ChangeUi.EditedBoth
        },
        canKeepMine = ConflictDecision.Choice.KeepLocal in choices,
        canKeepTheirs = ConflictDecision.Choice.KeepRemote in choices,
        canKeepBoth = ConflictDecision.Choice.KeepBoth in choices,
    )

    private val FileConflict.uiId: String
        get() = "$sourceId/$fileId"

    private fun FileTransfer.toRunningUi() = TransferUi.Running(
        id = "running-${key.direction}",
        fileName = path.fileName(),
        direction = key.direction,
        progress = progress,
        transferred = FileSize(transferredBytes),
        total = FileSize(totalBytes),
        bytesPerSecond = bytesPerSecond,
        eta = eta,
    )

    private fun FileTransfer.toInterruptedUi() = TransferUi.Interrupted(
        id = key.id,
        fileName = path.fileName(),
        direction = key.direction,
        stoppedAtPercent = ((progress ?: 0f) * 100).toInt(),
    )

    private data class Now(
        val running: List<TransferUi.Running>,
        val queued: ActivityState.QueuedUi?,
        val interrupted: List<TransferUi.Interrupted>,
    )

    private fun Flow<Now>.holdingCards(): Flow<Now> = flow {
        var shown: Now? = null

        emitAll(transformLatest { now ->
            val previous = shown
            val gone = previous?.running.orEmpty().filter { old -> now.running.none { it.direction == old.direction } }
            val queuedGone = previous?.queued != null && now.queued == null

            if (gone.isEmpty() && !queuedGone) {
                shown = now
                emit(now)
                return@transformLatest
            }

            val held = now.copy(
                running = (now.running + gone).sortedBy { it.direction },
                queued = now.queued ?: previous?.queued,
            )
            shown = held
            emit(held)

            delay(CardHold)
            shown = now
            emit(now)
        })
    }

    private data class JournalState(
        val issues: List<JournalEntryUi>,
        val history: List<ActivityState.HistoryUi>,
    )

    private companion object {
        val CardHold = 1.5.seconds

        const val SampleFreed = "12.4 GB"
        const val SampleQuota = "61 %"
    }
}

private fun List<FileTransfer>.active(): List<FileTransfer> = FileTransfer.Direction.entries.mapNotNull { direction ->
    val ofDirection = filter { it.key.direction == direction }
    ofDirection.filter { it.state == FileTransfer.State.Running }.maxByOrNull { it.startedAt }
        ?: ofDirection
            .takeIf { all -> all.any { it.state == FileTransfer.State.Queued } }
            ?.filter { it.state == FileTransfer.State.Completed }
            ?.maxByOrNull { it.updatedAt }
}

private fun List<FileTransfer>.queued(): ActivityState.QueuedUi? {
    val queued = filter { it.state == FileTransfer.State.Queued }
    if (queued.isEmpty()) return null

    return ActivityState.QueuedUi(
        outgoing = queued.count { it.key.direction == FileTransfer.Direction.Outgoing },
        incoming = queued.count { it.key.direction == FileTransfer.Direction.Incoming },
        bytes = queued.sumOf { it.totalBytes.coerceAtLeast(0) },
    )
}
