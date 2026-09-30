package com.fserver.core.sync.progress.impl

import com.fserver.core.sync.progress.FileTransfer
import com.fserver.core.sync.progress.FileTransferKey
import com.fserver.core.sync.progress.IndexingProgress
import com.fserver.core.sync.progress.OneShotFileTransfer
import com.fserver.core.sync.progress.SourcePass
import com.fserver.core.sync.progress.SyncFailureReason
import com.fserver.core.sync.progress.SyncProgressRepository
import com.fserver.core.sync.progress.toSyncFailure
import com.fserver.core.network.dictionary.dto.UploadKey
import com.fserver.core.util.TimeProvider
import com.fserver.files.upload.FileAction
import kotlinx.coroutines.flow.Flow

/**
 * The engine's side of [SyncProgressRepository]: every state change goes through here.
 *
 * Maps behind `StateFlow`s rather than a bus of events, because a screen that subscribes
 * halfway through a pass has to see where the pass already is, not only what happens next.
 * The three trackers are independent; this class is where one event touches more than one.
 */
internal class SyncProgressReporter(
    timeProvider: TimeProvider,
) : SyncProgressRepository {
    private val passState = PassTracker(timeProvider)
    private val transferState = TransferTracker<FileTransferKey, FileTransfer>(timeProvider) { key, entry ->
        FileTransfer(
            key = key,
            path = entry.path,
            totalBytes = entry.totalBytes,
            transferredBytes = entry.transferredBytes,
            state = entry.state,
            startedAt = entry.startedAt,
            updatedAt = entry.updatedAt,
        )
    }
    private val oneShotState = TransferTracker<OneShotKey, OneShotFileTransfer>(timeProvider) { key, entry ->
        OneShotFileTransfer(
            transferId = key.transferId,
            index = key.index,
            direction = key.direction,
            name = entry.path,
            totalBytes = entry.totalBytes,
            transferredBytes = entry.transferredBytes,
            state = entry.state,
            startedAt = entry.startedAt,
            updatedAt = entry.updatedAt,
        )
    }
    private val indexingState = IndexingTracker(timeProvider)

    override val passes: Flow<List<SourcePass>> get() = passState.passes

    override val transfers: Flow<List<FileTransfer>> get() = transferState.transfers

    override val oneShotTransfers: Flow<List<OneShotFileTransfer>> get() = oneShotState.transfers

    override fun oneShot(transferId: String): Flow<List<OneShotFileTransfer>> =
        oneShotState.transfers { it.transferId == transferId }

    override fun pass(sourceId: String): Flow<SourcePass?> = passState.pass(sourceId)

    override fun indexing(sourceId: String): Flow<IndexingProgress?> = indexingState.indexing(sourceId)

    override fun clearFinished() {
        passState.clearFinished()
        transferState.clearFinished()
        oneShotState.clearFinished()
        indexingState.clearFinished()
    }

    fun indexingStarted(sourceId: String) = indexingState.started(sourceId)

    fun indexingScanned(sourceId: String, files: Int, bytes: Long) = indexingState.scanned(sourceId, files, bytes)

    /** [done] of [total] possible renames hashed. A local pass still indexing shows it as its stage. */
    fun indexingHashing(sourceId: String, done: Int, total: Int) {
        indexingState.hashing(sourceId, done, total)

        passState.updateLocal(sourceId) {
            if (it.stage == SourcePass.Local.Stage.Scanning) it.copy(stage = SourcePass.Local.Stage.Hashing) else it
        }
    }

    fun indexingFinished(sourceId: String, failed: Boolean) = indexingState.finished(sourceId, failed)

    /**
     * A pass this device drives has begun, the lease for it already held. Replaces whatever the
     * previous pass over the same source left behind - including a [SourcePass.Remote] the peer
     * finished with.
     */
    fun localPassStarted(sourceId: String) = passState.localStarted(sourceId)

    fun localPassStage(sourceId: String, stage: SourcePass.Local.Stage) =
        passState.updateLocal(sourceId) { it.copy(stage = stage) }

    /**
     * What this round will run, added to what earlier rounds already planned.
     *
     * A pass re-plans after hashing, so the total only grows - counting each round on its own
     * would reset the bar to zero every time the plan is redrawn.
     */
    fun localPassPlanned(sourceId: String, actions: List<FileAction>) {
        passState.updateLocal(sourceId) {
            it.copy(
                stage = SourcePass.Local.Stage.Transferring,
                actionsPlanned = it.actionsPlanned + actions.size,
            )
        }

        for (action in actions) {
            val key = transferKey(sourceId, action) ?: continue
            val file = when (action) {
                is FileAction.Upload -> action.file
                is FileAction.Download -> action.file
                else -> continue
            }

            transferState.queued(key, file.path, file.metadata.size)
        }
    }

    /** One planned action is through, [failure] saying whether it got there. */
    fun localPassAdvanced(sourceId: String, action: FileAction, failure: Throwable?) {
        passState.updateLocal(sourceId) { it.copy(actionsDone = it.actionsDone + 1) }

        val key = transferKey(sourceId, action) ?: return

        if (failure != null) {
            transferState.failed(key, failure)
        } else {
            transferState.settled(key)
        }
    }

    /** [count] distinct files this pass has left out for a file limit so far. */
    fun localPassSkipped(sourceId: String, count: Int) =
        passState.updateLocal(sourceId) { it.copy(filesSkipped = count) }

    fun localPassFinished(sourceId: String, failure: Throwable?) = passState.updateLocal(sourceId) {
        it.copy(
            stage = if (failure == null) SourcePass.Local.Stage.Finished else SourcePass.Local.Stage.Failed,
            failure = failure?.toSyncFailure(),
        )
    }

    /**
     * A pass that never got as far as a lease - the peer could not be dialled, or would not
     * answer. Recorded so the source reads as failed rather than as one that simply did not move.
     */
    fun localPassAborted(sourceId: String, failure: Throwable) = passState.localAborted(sourceId, failure)

    /**
     * [peerDeviceId] has taken the lease on [sourceId] and is driving a pass against this device.
     * Reported from the lease: a pass that asks for nothing is still a pass.
     */
    fun remotePassStarted(sourceId: String, peerDeviceId: String) = passState.remoteStarted(sourceId, peerDeviceId)

    /**
     * The peer's lease is gone. [stage] says whether it was handed back or simply lost, and
     * [failure] what the peer said went wrong when it handed it back having failed.
     */
    fun remotePassFinished(
        sourceId: String,
        stage: SourcePass.Remote.Stage,
        failure: SyncFailureReason? = null,
    ) = passState.remoteFinished(sourceId, stage, failure)

    // One upload, whichever kind of key it moves under: a source's file or a one-shot's.

    fun uploadStarted(direction: FileTransfer.Direction, key: UploadKey, path: String, totalBytes: Long) =
        when (key) {
            is UploadKey.Source -> transferState.started(key.toTransferKey(direction), path, totalBytes)
            is UploadKey.OneShot -> oneShotState.started(key.toOneShotKey(direction), path, totalBytes)
        }

    fun uploadAdvanced(direction: FileTransfer.Direction, key: UploadKey, transferredBytes: Long) = when (key) {
        is UploadKey.Source -> transferState.advanced(key.toTransferKey(direction), transferredBytes)
        is UploadKey.OneShot -> oneShotState.advanced(key.toOneShotKey(direction), transferredBytes)
    }

    fun uploadCompleted(direction: FileTransfer.Direction, key: UploadKey) = when (key) {
        is UploadKey.Source -> transferState.completed(key.toTransferKey(direction))
        is UploadKey.OneShot -> oneShotState.completed(key.toOneShotKey(direction))
    }

    fun uploadFailed(direction: FileTransfer.Direction, key: UploadKey, failure: Throwable?) = when (key) {
        is UploadKey.Source -> transferState.failed(key.toTransferKey(direction), failure)
        is UploadKey.OneShot -> oneShotState.failed(key.toOneShotKey(direction), failure)
    }

    /** No bytes moved - the receiver declined it, or the sender gave it up - so it is dropped, not failed. */
    fun uploadSkipped(direction: FileTransfer.Direction, key: UploadKey) = when (key) {
        is UploadKey.Source -> transferState.skipped(key.toTransferKey(direction))
        is UploadKey.OneShot -> oneShotState.skipped(key.toOneShotKey(direction))
    }

    private data class OneShotKey(val direction: FileTransfer.Direction, val transferId: String, val index: Int)

    private fun UploadKey.Source.toTransferKey(direction: FileTransfer.Direction) =
        FileTransferKey(direction, sourceId, fileId)

    private fun UploadKey.OneShot.toOneShotKey(direction: FileTransfer.Direction) =
        OneShotKey(direction, transferId, index)

    private fun transferKey(sourceId: String, action: FileAction): FileTransferKey? = when (action) {
        is FileAction.Upload -> FileTransferKey.outgoing(sourceId, action.id.value)
        is FileAction.Download -> FileTransferKey.incoming(sourceId, action.id.value)
        else -> null
    }
}
