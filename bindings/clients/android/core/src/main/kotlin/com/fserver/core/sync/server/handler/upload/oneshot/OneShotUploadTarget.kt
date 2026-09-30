package com.fserver.core.sync.server.handler.upload.oneshot

import com.fserver.common.exception.TransferException
import com.fserver.common.model.ContentHash
import com.fserver.core.files.scan.toFiles
import com.fserver.core.network.dictionary.FileServerMessages.Upload
import com.fserver.core.network.dictionary.dto.UploadKey
import com.fserver.core.oneshot.impl.settledStatus
import com.fserver.core.oneshot.model.OneShotTransfer
import com.fserver.core.oneshot.model.OneShotTransferFile
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.server.handler.upload.SessionUploads
import com.fserver.core.sync.server.handler.upload.UploadLanding
import com.fserver.core.sync.server.handler.upload.UploadTarget
import com.fserver.core.util.TimeProvider
import com.fserver.files.FilesNode
import com.fserver.files.fs.FsFile
import com.fserver.net.security.identity.PeerIdentity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * A one-shot transfer's file: sent only by the transfer's peer while it is accepted, staged in
 * [OneShotStaging], placed in the transfer's destination under a name that replaces nothing.
 *
 * A transfer canceled or settled meanwhile answers [Upload.Stopped] at the next request, which is
 * what ends the sender's side.
 */
internal class OneShotUploadTarget(
    private val storage: FServerStorage,
    private val node: FilesNode,
    private val staging: OneShotStaging,
    private val timeProvider: TimeProvider,
) : UploadTarget {
    private val store get() = storage.oneShotTransfers

    /** Picking a free name and taking it is one step: `place` replaces whatever is there. */
    private val placeLock = Mutex()

    override suspend fun open(
        peer: PeerIdentity,
        init: Upload.Init,
        uploads: SessionUploads,
    ): UploadTarget.Opening {
        val key = init.key as UploadKey.OneShot

        val (_, file) = try {
            accepted(peer, key)
        } catch (e: TransferException.UploadStoppedException) {
            return UploadTarget.Opening.Answered(Upload.Stopped(key, e.message.orEmpty()))
        }

        if (file.status == OneShotTransferFile.Status.Completed) {
            return UploadTarget.Opening.Answered(Upload.Completed(key))
        }

        val (staged, committed) = staging.open(key.transferId, key.index, file.committedBytes)

        return UploadTarget.Opening.Staged(Landing(peer, key, file), staged, committed)
    }

    override suspend fun abandon(peer: PeerIdentity, key: UploadKey, reason: String) {
        key as UploadKey.OneShot
        val (_, file) = accepted(peer, key)

        store.updateFile(
            key.transferId,
            file.copy(status = OneShotTransferFile.Status.Failed(reason.take(MaxReasonLength)))
        )
        finishIfSettled(key.transferId)
    }

    /**
     * The transfer and file [key] names, while [peer] may send it: the transfer is its, incoming
     * here, and accepted. Anything else stops the upload - and tells a stranger nothing more.
     */
    private suspend fun accepted(
        peer: PeerIdentity,
        key: UploadKey.OneShot
    ): Pair<OneShotTransfer, OneShotTransferFile> {
        val transfer = store.find(key.transferId)
            ?.takeIf { it.peer.deviceId == peer.deviceId && it.direction is OneShotTransfer.Direction.Incoming }
            ?: throw TransferException.UploadStoppedException(UnknownReason)

        if (transfer.status != OneShotTransfer.Status.Active) {
            throw TransferException.UploadStoppedException("Transfer is ${transfer.status}")
        }

        val file = transfer.files.find { it.index == key.index }
            ?: throw IllegalArgumentException("No file #${key.index} in transfer ${key.transferId}")

        return transfer to file
    }

    private suspend fun finishIfSettled(transferId: String) {
        val transfer = store.find(transferId) ?: return
        val status = settledStatus(transfer.files) ?: return

        store.updateStatus(transferId, status, timeProvider.now())
        staging.discard(transferId)
    }

    private inner class Landing(
        private val peer: PeerIdentity,
        private val key: UploadKey.OneShot,
        private val file: OneShotTransferFile,
    ) : UploadLanding {
        override val size: Long get() = file.size

        override val path: String get() = file.name

        override suspend fun ensureOpen(peer: PeerIdentity) {
            accepted(peer, key)
        }

        override suspend fun checkpoint(offset: Long) {
            store.checkpoint(key.transferId, key.index, offset)
        }

        override suspend fun place(staged: FsFile, hash: ContentHash) {
            val (transfer, _) = accepted(peer, key)
            val destination = (transfer.direction as OneShotTransfer.Direction.Incoming).destination
                ?: error("Active transfer ${transfer.id} has no destination")

            val fs = node.openSource(destination.toFiles())
            val placed = placeLock.withLock {
                fs.place(staged, freeName(fs, safeFileName(file.name)))
            }

            store.updateFile(
                transfer.id,
                file.copy(
                    locator = placed.locator,
                    committedBytes = file.size,
                    status = OneShotTransferFile.Status.Completed
                ),
            )

            Timber.i("Received $key into ${placed.locator}")
            finishIfSettled(transfer.id)
        }

        override suspend fun discard(staged: FsFile) {
            staged.delete()
            store.checkpoint(key.transferId, key.index, 0)
        }
    }

    private companion object {
        const val MaxReasonLength = 500
        const val UnknownReason = "Unknown transfer"
    }
}
