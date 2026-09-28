package com.fserver.core.sync.progress

import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.progress.SyncProgressReporter.Companion.ProgressStepBytes
import com.fserver.core.util.TimeProvider
import com.fserver.files.upload.FileAction
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/**
 * The engine's side of [SyncProgressRepository]: every state change goes through here.
 *
 * Maps behind `StateFlow`s rather than a bus of events, because a screen that subscribes
 * halfway through a pass has to see where the pass already is, not only what happens next.
 */
internal class SyncProgressReporter(
    private val timeProvider: TimeProvider,
) : SyncProgressRepository {
    private val passState = MutableStateFlow<Map<String, SourcePass>>(emptyMap())
    private val transferState = MutableStateFlow<Map<FileTransferKey, FileTransfer>>(emptyMap())
    private val indexingState = MutableStateFlow<Map<String, IndexingProgress>>(emptyMap())

    override val passes: Flow<List<SourcePass>> =
        passState.map { passes -> passes.values.sortedBy { it.startedAt } }

    override val transfers: Flow<List<FileTransfer>> =
        transferState.map { transfers -> transfers.values.sortedBy { it.startedAt } }

    override fun pass(sourceId: String): Flow<SourcePass?> =
        passState.map { it[sourceId] }.distinctUntilChanged()

    override fun indexing(sourceId: String): Flow<IndexingProgress?> =
        indexingState.map { it[sourceId] }.distinctUntilChanged()

    override fun clearFinished() {
        passState.update { passes -> passes.filterValues { !it.isFinished } }
        transferState.update { transfers -> transfers.filterValues { !it.isFinished } }
        indexingState.update { runs -> runs.filterValues { !it.isFinished } }
    }

    fun indexingStarted(sourceId: String) {
        val now = timeProvider.now()

        indexingState.update {
            it + (sourceId to IndexingProgress(
                sourceId = sourceId,
                stage = IndexingProgress.Stage.Scanning,
                startedAt = now,
                updatedAt = now,
            ))
        }
    }

    fun indexingScanned(sourceId: String, files: Int, bytes: Long) =
        updateIndexing(sourceId) { it.copy(filesScanned = files, bytesScanned = bytes) }

    /** [done] of [total] possible renames hashed. A local pass still indexing shows it as its stage. */
    fun indexingHashing(sourceId: String, done: Int, total: Int) {
        updateIndexing(sourceId) {
            it.copy(stage = IndexingProgress.Stage.Hashing, filesHashed = done, filesToHash = total)
        }

        updateLocalPass(sourceId) {
            if (it.stage == SourcePass.Local.Stage.Scanning) it.copy(stage = SourcePass.Local.Stage.Hashing) else it
        }
    }

    fun indexingFinished(sourceId: String, failed: Boolean) = updateIndexing(sourceId) {
        it.copy(stage = if (failed) IndexingProgress.Stage.Failed else IndexingProgress.Stage.Finished)
    }

    /**
     * A pass this device drives has begun, the lease for it already held. Replaces whatever the
     * previous pass over the same source left behind - including a [SourcePass.Remote] the peer
     * finished with.
     */
    fun localPassStarted(sourceId: String) {
        val now = timeProvider.now()

        passState.update {
            it + (sourceId to SourcePass.Local(
                sourceId = sourceId,
                startedAt = now,
                updatedAt = now,
                stage = SourcePass.Local.Stage.Scanning,
            ))
        }
    }

    fun localPassStage(sourceId: String, stage: SourcePass.Local.Stage) {
        updateLocalPass(sourceId) { it.copy(stage = stage) }
    }

    /**
     * What this round will run, added to what earlier rounds already planned.
     *
     * A pass re-plans after hashing, so the total only grows - counting each round on its own
     * would reset the bar to zero every time the plan is redrawn.
     */
    fun localPassPlanned(sourceId: String, actions: List<FileAction>) {
        updateLocalPass(sourceId) {
            it.copy(
                stage = SourcePass.Local.Stage.Transferring,
                actionsPlanned = it.actionsPlanned + actions.size,
            )
        }

        actions.forEach { action -> transferKey(sourceId, action)?.let { queue(it, action) } }
    }

    /** One planned action is through, [failure] saying whether it got there. */
    fun localPassAdvanced(sourceId: String, action: FileAction, failure: Throwable?) {
        updateLocalPass(sourceId) { it.copy(actionsDone = it.actionsDone + 1) }

        val key = transferKey(sourceId, action) ?: return

        if (failure != null) {
            transferFailed(key, failure)
            return
        }

        // Whoever moved the bytes already marked it. A transfer still sitting at Queued moved
        // none - the peer had it, or the action turned out to be a no-op.
        updateTransfer(key) { transfer ->
            if (transfer.state == FileTransfer.State.Queued) {
                transfer.copy(state = FileTransfer.State.Completed)
            } else {
                transfer
            }
        }
    }

    /** [count] distinct files this pass has left out for a file limit so far. */
    fun localPassSkipped(sourceId: String, count: Int) {
        updateLocalPass(sourceId) { it.copy(filesSkipped = count) }
    }

    fun localPassFinished(sourceId: String, failure: Throwable?) {
        updateLocalPass(sourceId) {
            it.copy(
                stage = if (failure == null) {
                    SourcePass.Local.Stage.Finished
                } else {
                    SourcePass.Local.Stage.Failed
                },
                failure = failure?.toSyncFailure(),
            )
        }
    }

    /**
     * A pass that never got as far as a lease - the peer could not be dialled, or would not
     * answer. Recorded so the source reads as failed rather than as one that simply did not move.
     *
     * A pass the peer is still driving is left alone: the lease it holds is the reason this one
     * got nowhere, and overwriting it would report the peer's work as our failure.
     */
    fun localPassAborted(sourceId: String, failure: Throwable) {
        val now = timeProvider.now()

        passState.update { passes ->
            val existing = passes[sourceId]
            if (existing is SourcePass.Remote && !existing.isFinished) return@update passes

            passes + (sourceId to SourcePass.Local(
                sourceId = sourceId,
                startedAt = now,
                updatedAt = now,
                stage = SourcePass.Local.Stage.Failed,
                failure = failure.toSyncFailure(),
            ))
        }
    }

    /**
     * [peerDeviceId] has taken the lease on [sourceId] and is driving a pass against this device.
     *
     * Reported from the lease rather than from the requests that follow, because the requests are
     * the only other evidence there is and a pass that asks for nothing is still a pass.
     */
    fun remotePassStarted(sourceId: String, peerDeviceId: String) {
        val now = timeProvider.now()

        passState.update {
            it + (sourceId to SourcePass.Remote(
                sourceId = sourceId,
                startedAt = now,
                updatedAt = now,
                peerDeviceId = peerDeviceId,
                stage = SourcePass.Remote.Stage.Serving,
            ))
        }
    }

    /**
     * The peer's lease is gone. [stage] says whether it was handed back or simply lost, and
     * [failure] what the peer said went wrong when it handed it back having failed.
     */
    fun remotePassFinished(
        sourceId: String,
        stage: SourcePass.Remote.Stage,
        failure: SyncFailureReason? = null,
    ) {
        passState.update { passes ->
            val existing = passes[sourceId] as? SourcePass.Remote ?: return@update passes

            passes + (sourceId to existing.copy(
                stage = stage,
                failure = failure,
                updatedAt = timeProvider.now(),
            ))
        }
    }

    /**
     * Bytes are about to move. Registers the transfer outright, so a file a peer pushed without
     * this device planning it still shows up.
     *
     * A queued entry is replaced rather than updated: the rate is measured from the first byte,
     * not from the moment the pass decided the file had to move.
     */
    fun transferStarted(key: FileTransferKey, path: String, totalBytes: Long) {
        val now = timeProvider.now()

        transferState.update { transfers ->
            transfers + (key to FileTransfer(
                key = key,
                path = path,
                totalBytes = totalBytes,
                transferredBytes = 0L,
                state = FileTransfer.State.Running,
                startedAt = now,
                updatedAt = now,
            ))
        }
    }

    /**
     * [transferredBytes] bytes of the file are across.
     *
     * Chunks are frame-sized, so a big file reports thousands of times; anything under
     * [ProgressStepBytes] leaves the map untouched and the `StateFlow` therefore silent.
     */
    fun transferAdvanced(key: FileTransferKey, transferredBytes: Long) {
        transferState.update { transfers ->
            val existing = transfers[key] ?: return@update transfers
            if (transferredBytes - existing.transferredBytes < ProgressStepBytes) return@update transfers

            transfers + (key to existing.copy(
                transferredBytes = transferredBytes,
                state = FileTransfer.State.Running,
                updatedAt = timeProvider.now(),
            ))
        }
    }

    fun transferCompleted(key: FileTransferKey) {
        updateTransfer(key) {
            it.copy(
                transferredBytes = maxOf(it.transferredBytes, it.totalBytes),
                state = FileTransfer.State.Completed,
            )
        }

        pruneFinishedTransfers()
    }

    fun transferFailed(key: FileTransferKey, failure: Throwable?) {
        updateTransfer(key) { it.copy(state = FileTransfer.State.Failed(failure?.message)) }

        pruneFinishedTransfers()
    }

    /** The receiver declined the file for its limits: no bytes moved, so it is dropped rather than failed. */
    fun transferSkipped(key: FileTransferKey) {
        transferState.update { it - key }
    }

    private fun queue(key: FileTransferKey, action: FileAction) {
        val file = when (action) {
            is FileAction.Upload -> action.file
            is FileAction.Download -> action.file
            else -> return
        }

        val now = timeProvider.now()

        transferState.update { transfers ->
            // A retry of a file already moving keeps the transfer that is moving it.
            if (transfers[key]?.isFinished == false) return@update transfers

            transfers + (key to FileTransfer(
                key = key,
                path = file.path,
                totalBytes = file.metadata.size,
                transferredBytes = 0L,
                state = FileTransfer.State.Queued,
                startedAt = now,
                updatedAt = now,
            ))
        }
    }

    /** The list is unbounded otherwise: every completed file would sit in it until shutdown. */
    private fun pruneFinishedTransfers() {
        transferState.update { transfers ->
            val finished = transfers.values.filter { it.isFinished }
            if (finished.size <= MaxFinishedTransfers) return@update transfers

            val dropped = finished
                .sortedBy { it.updatedAt }
                .take(finished.size - MaxFinishedTransfers)
                .map { it.key }

            transfers - dropped.toSet()
        }
    }

    /**
     * Ignores a source the peer holds: a local report against a remote pass would mean the lease
     * let both sides run at once, and overwriting it would hide that rather than show it.
     */
    private fun updateLocalPass(sourceId: String, transform: (SourcePass.Local) -> SourcePass.Local) {
        passState.update { passes ->
            val existing = passes[sourceId] as? SourcePass.Local ?: return@update passes
            passes + (sourceId to transform(existing).copy(updatedAt = timeProvider.now()))
        }
    }

    private fun updateIndexing(sourceId: String, transform: (IndexingProgress) -> IndexingProgress) {
        indexingState.update { runs ->
            val existing = runs[sourceId] ?: return@update runs
            runs + (sourceId to transform(existing).copy(updatedAt = timeProvider.now()))
        }
    }

    private fun updateTransfer(key: FileTransferKey, transform: (FileTransfer) -> FileTransfer) {
        transferState.update { transfers ->
            val existing = transfers[key] ?: return@update transfers
            transfers + (key to transform(existing).copy(updatedAt = timeProvider.now()))
        }
    }

    private fun transferKey(sourceId: String, action: FileAction): FileTransferKey? = when (action) {
        is FileAction.Upload -> FileTransfer.Direction.Outgoing
        is FileAction.Download -> FileTransfer.Direction.Incoming
        else -> null
    }?.let { FileTransferKey(direction = it, sourceId = sourceId, fileId = action.id.value) }

    companion object {
        /** Sending side of a transfer this device drives. */
        fun outgoing(source: SourceEntry, fileId: String) =
            FileTransferKey(FileTransfer.Direction.Outgoing, source.id, fileId)

        /** Receiving side, whether we asked for the file or the peer pushed it. */
        fun incoming(sourceId: String, fileId: String) =
            FileTransferKey(FileTransfer.Direction.Incoming, sourceId, fileId)

        /** Bytes a transfer must advance by before it is worth waking every collector. */
        private const val ProgressStepBytes = 256L * 1024

        private const val MaxFinishedTransfers = 100
    }
}
