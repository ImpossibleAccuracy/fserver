package com.fserver.core.oneshot.impl

import com.fserver.common.exception.FileSystemException
import com.fserver.common.exception.SyncException
import com.fserver.common.exception.TransferException
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.di.BackgroundScope
import com.fserver.core.network.dictionary.FileServerMessages.Upload
import com.fserver.core.network.dictionary.dto.UploadKey
import com.fserver.core.oneshot.model.OneShotTransfer
import com.fserver.core.oneshot.model.OneShotTransferFile
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.remote.PeerConnector
import com.fserver.core.sync.transfer.FilePusher
import com.fserver.core.util.TimeProvider
import com.fserver.files.FilesNode
import com.fserver.files.fs.FileSystemSource
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap

/**
 * Pushes the files of accepted outgoing transfers, one job per transfer, through the same
 * [FilePusher] sync uses.
 *
 * A job that fails on the link leaves the transfer active: [OneShotExchange.resume] starts it
 * again when the peer is back. Only the peer stopping it, or every file settling, ends it.
 */
internal class OneShotSender(
    private val storage: FServerStorage,
    private val node: FilesNode,
    private val peers: PeerConnector,
    private val pusher: FilePusher,
    private val timeProvider: TimeProvider,
    private val backgroundScope: BackgroundScope,
) {
    private val store get() = storage.oneShotTransfers

    private val jobs = ConcurrentHashMap<String, Job>()

    /** Starts pushing [transferId], unless it already is. */
    fun start(transferId: String) {
        val job = backgroundScope.launch(start = CoroutineStart.LAZY) {
            runCatchingCancellable { send(transferId) }
                .onFailure { Timber.w(it, "Sending transfer $transferId stopped; resumes when the peer is back") }
        }

        if (jobs.putIfAbsent(transferId, job) != null) return

        job.invokeOnCompletion { jobs.remove(transferId, job) }
        job.start()
    }

    suspend fun stop(transferId: String) {
        jobs[transferId]?.cancelAndJoin()
    }

    private suspend fun send(transferId: String) {
        val transfer = store.find(transferId) ?: return
        if (transfer.direction != OneShotTransfer.Direction.Outgoing) return
        if (transfer.status != OneShotTransfer.Status.Active) return

        val session = peers.connectToDevice(transfer.peer.deviceId)
        val fs = node.openSource(FileSystemSource.Shared(transfer.files.mapNotNull { it.locator }))

        for (file in transfer.files.filter { it.status == OneShotTransferFile.Status.Pending }) {
            val key = UploadKey.OneShot(transferId, file.index)
            val locator = file.locator ?: error("Outgoing file #${file.index} of $transferId has no locator")

            val outcome = try {
                pusher.push(
                    session = session,
                    init = Upload.Init(key),
                    file = fs.openFile(locator) ?: throw FileSystemException.InvalidPath(locator),
                    path = file.name,
                    size = file.size,
                    knownHash = null,
                )
                OneShotTransferFile.Status.Completed
            } catch (e: TransferException.UploadStoppedException) {
                Timber.i("Device ${transfer.peer.deviceId} stopped transfer $transferId: ${e.message}")
                store.updateStatus(transferId, OneShotTransfer.Status.Failed(e.message.orEmpty()), timeProvider.now())
                return
            } catch (e: FileSystemException) {
                // The grant on the shared uri is gone, or the file is: nothing to retry with.
                val reason = e.message ?: "Cannot read the file anymore"
                pusher.abandon(session, key, reason)
                OneShotTransferFile.Status.Failed(reason)
            } catch (e: SyncException.RemoteRejectedException) {
                // The receiver keeps refusing the bytes: another run would only repeat it.
                OneShotTransferFile.Status.Failed(e.message ?: "Refused by the receiver")
            }

            store.updateFile(
                transferId,
                file.copy(
                    committedBytes = if (outcome == OneShotTransferFile.Status.Completed) file.size else file.committedBytes,
                    status = outcome,
                ),
            )
        }

        val settled = store.find(transferId)?.let { settledStatus(it.files) } ?: return
        store.updateStatus(transferId, settled, timeProvider.now())
    }
}
