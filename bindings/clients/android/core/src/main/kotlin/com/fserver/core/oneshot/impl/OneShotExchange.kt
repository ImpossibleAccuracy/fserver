package com.fserver.core.oneshot.impl

import com.fserver.common.exception.TransferException
import com.fserver.common.utils.IdGenerator
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.di.BackgroundScope
import com.fserver.core.files.SourceLocation
import com.fserver.core.files.scan.ScannedContent
import com.fserver.core.files.scan.toFiles
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.dto.OneShotFileDto
import com.fserver.core.oneshot.model.OneShotTransfer
import com.fserver.core.oneshot.model.OneShotTransferFile
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.remote.PeerConnector
import com.fserver.core.sync.server.handler.upload.oneshot.OneShotStaging
import com.fserver.core.util.TimeProvider
import com.fserver.files.FilesNode
import com.fserver.net.security.identity.PeerIdentity
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.UUID

/**
 * Setting a one-shot transfer up and tearing it down, both sides: offer, answer, cancel. Bytes are
 * [OneShotSender]'s and `OneShotUploadTarget`'s.
 *
 * Every message names a peer-chosen transfer id, so each is checked against the peer the transfer
 * was recorded with before it changes anything - a device may only touch its own transfers.
 */
internal class OneShotExchange(
    private val storage: FServerStorage,
    private val node: FilesNode,
    private val peers: PeerConnector,
    private val sender: OneShotSender,
    private val staging: OneShotStaging,
    private val outbox: OneShotOutbox,
    private val timeProvider: TimeProvider,
    private val backgroundScope: BackgroundScope,
) {
    private val store get() = storage.oneShotTransfers

    /**
     * Records an outgoing transfer of [files], all found in [origin], and offers it to [deviceId].
     * Fails on any file already gone: better now, in front of the user, than mid-transfer.
     */
    suspend fun create(
        deviceId: String,
        origin: SourceLocation.Persistable,
        files: List<ScannedContent.File>,
    ): OneShotTransfer {
        requireSendable(files.map { it.locator })

        val fs = node.openSource(origin.toFiles())
        for (file in files) {
            fs.openFile(file.locator) ?: throw TransferException.FileNotFoundException("${file.path} is gone")
        }

        val transferFiles = files.mapIndexed { index, file ->
            OneShotTransferFile(
                index = index,
                name = file.path.substringAfterLast('/'),
                size = file.size.bytes,
                locator = file.locator,
            )
        }

        return record(IdGenerator.nextId, deviceId, origin, transferFiles)
    }

    /** [create] for the `content://` [uris] another app shared, sent from copies in [OneShotOutbox]. */
    suspend fun createShared(deviceId: String, uris: List<String>): OneShotTransfer {
        requireSendable(uris)

        val id = IdGenerator.nextId
        val files = outbox.fill(id, uris)

        return record(id, deviceId, OneShotOutbox.Location, files)
    }

    private fun requireSendable(locators: List<String>) {
        require(locators.isNotEmpty()) { "Nothing to send" }
        require(locators.size <= MaxFiles) { "At most $MaxFiles files per transfer" }
        require(locators.toSet().size == locators.size) { "The same file twice" }
    }

    private suspend fun record(
        id: String,
        deviceId: String,
        origin: SourceLocation.Persistable,
        files: List<OneShotTransferFile>,
    ): OneShotTransfer {
        val transfer = OneShotTransfer(
            id = id,
            peer = OneShotTransfer.Peer(deviceId = deviceId, displayName = peerName(deviceId)),
            direction = OneShotTransfer.Direction.Outgoing(origin),
            status = OneShotTransfer.Status.Pending,
            files = files,
            createdAt = timeProvider.now(),
        )

        if (!store.insert(transfer)) {
            outbox.release(transfer)
            error("Transfer id $id is taken")
        }

        // Best effort: the peer may be away, and the offer goes again when it is back.
        backgroundScope.launch { offer(transfer) }

        return transfer
    }

    /**
     * Picks up where a restart or a lost link left the outgoing transfers to [deviceId], or to
     * everyone when null: an unanswered one is offered again, an accepted one resumes sending.
     */
    suspend fun resume(deviceId: String? = null) {
        val outgoing = store.unfinished().filter {
            it.direction is OneShotTransfer.Direction.Outgoing &&
                    (deviceId == null || it.peer.deviceId == deviceId)
        }

        for (transfer in outgoing) {
            when (transfer.status) {
                OneShotTransfer.Status.Pending -> offer(transfer)
                OneShotTransfer.Status.Active -> sender.start(transfer.id)
                else -> Unit
            }
        }
    }

    /** Parks what [peer] offered until this device's user answers. A re-offer is answered again. */
    suspend fun onOffer(peer: PeerIdentity, message: FileServerMessages.OneShot.Offer) {
        val existing = store.find(message.transferId)

        if (existing != null) {
            if (existing.peer.deviceId != peer.deviceId ||
                existing.direction is OneShotTransfer.Direction.Outgoing
            ) {
                Timber.w("Device ${peer.deviceId} offered transfer ${message.transferId}, which is not its to offer")
                return
            }

            // It only re-offers because our answer never arrived.
            when (existing.status) {
                OneShotTransfer.Status.Pending -> Unit
                OneShotTransfer.Status.Active,
                OneShotTransfer.Status.Completed -> decide(existing, accepted = true)
                OneShotTransfer.Status.Declined -> decide(existing, accepted = false)
                OneShotTransfer.Status.Cancelled,
                is OneShotTransfer.Status.Failed -> tell(existing, FileServerMessages.OneShot.Cancel(existing.id))
            }
            return
        }

        val files = runCatching { validated(message.files) }.getOrElse {
            Timber.w(it, "Device ${peer.deviceId} offered a malformed transfer ${message.transferId}")
            return
        }

        store.insert(
            OneShotTransfer(
                id = message.transferId,
                peer = OneShotTransfer.Peer(
                    deviceId = peer.deviceId,
                    displayName = message.senderName.take(MaxNameLength),
                ),
                direction = OneShotTransfer.Direction.Incoming(destination = null),
                status = OneShotTransfer.Status.Pending,
                files = files,
                createdAt = timeProvider.now(),
            )
        )
    }

    /** Accepts into [destination] and tells the sender to start. */
    suspend fun accept(transferId: String, destination: SourceLocation.Hostable) {
        val transfer = requireIncoming(transferId)

        check(store.accept(transferId, destination)) { "Transfer $transferId is no longer waiting for an answer" }

        decide(transfer, accepted = true)
    }

    suspend fun decline(transferId: String) {
        val transfer = requireIncoming(transferId)

        check(store.updateStatus(transferId, OneShotTransfer.Status.Declined, timeProvider.now())) {
            "Transfer $transferId is no longer waiting for an answer"
        }

        decide(transfer, accepted = false)
    }

    /** Stops [transferId] on this side and tells the peer. */
    suspend fun cancel(transferId: String) {
        val transfer = store.find(transferId) ?: throw IllegalArgumentException("No transfer $transferId")

        check(store.updateStatus(transferId, OneShotTransfer.Status.Cancelled, timeProvider.now())) {
            "Transfer $transferId has already finished"
        }

        stopLocally(transfer)
        tell(transfer, FileServerMessages.OneShot.Cancel(transferId, CancelledReason))
    }

    /** Sends an accepted outgoing transfer again, after the link or the peer failed it. */
    suspend fun retry(transferId: String) {
        val transfer = store.find(transferId) ?: throw IllegalArgumentException("No transfer $transferId")
        require(transfer.direction is OneShotTransfer.Direction.Outgoing) { "Only the sender drives a transfer" }

        when (transfer.status) {
            OneShotTransfer.Status.Pending -> offer(transfer)
            OneShotTransfer.Status.Active -> sender.start(transferId)
            else -> throw IllegalStateException("Transfer $transferId has already finished")
        }
    }

    /** The receiver's answer to our offer. */
    suspend fun onDecision(peer: PeerIdentity, message: FileServerMessages.OneShot.Decision) {
        val transfer = ownOutgoing(peer, message.transferId) ?: return

        if (!message.accepted) {
            store.updateStatus(transfer.id, OneShotTransfer.Status.Declined, timeProvider.now())
            outbox.release(transfer)
            return
        }

        // Active already: a repeated answer to a re-offer, and the sender may need a nudge.
        if (transfer.status == OneShotTransfer.Status.Pending) {
            store.updateStatus(transfer.id, OneShotTransfer.Status.Active, timeProvider.now())
        }

        sender.start(transfer.id)
    }

    suspend fun onCancel(peer: PeerIdentity, message: FileServerMessages.OneShot.Cancel) {
        val transfer = store.find(message.transferId)
            ?.takeIf { it.peer.deviceId == peer.deviceId }
            ?: return

        Timber.i("Device ${peer.deviceId} cancelled transfer ${transfer.id}: ${message.reason}")

        store.updateStatus(transfer.id, OneShotTransfer.Status.Cancelled, timeProvider.now())
        stopLocally(transfer)
    }

    private suspend fun stopLocally(transfer: OneShotTransfer) {
        when (transfer.direction) {
            is OneShotTransfer.Direction.Outgoing -> {
                sender.stop(transfer.id)
                outbox.release(transfer)
            }
            // Its uploads live in the peer's session and stop at their next request, which is
            // answered Stopped; what they staged goes now.
            is OneShotTransfer.Direction.Incoming -> staging.discard(transfer.id)
        }
    }

    private suspend fun offer(transfer: OneShotTransfer) {
        val offer = FileServerMessages.OneShot.Offer(
            transferId = transfer.id,
            senderName = storage.identity.localDevice().displayName,
            files = transfer.files.map {
                OneShotFileDto(index = it.index, name = it.name, size = it.size)
            },
        )

        tell(transfer, offer)
    }

    private suspend fun decide(transfer: OneShotTransfer, accepted: Boolean) {
        tell(transfer, FileServerMessages.OneShot.Decision(transfer.id, accepted))
    }

    /**
     * Best effort on purpose: the change is already recorded here, and a link that died on the way
     * out must not make the user's action look failed. The sender re-offers otherwise.
     */
    private suspend fun tell(transfer: OneShotTransfer, message: FileServerMessages.OneShot) {
        runCatchingCancellable {
            peers.connectToDevice(transfer.peer.deviceId).send(message).getOrThrow()
        }.onFailure {
            Timber.w(it, "Could not send ${message::class.simpleName} of transfer ${transfer.id} to ${transfer.peer.deviceId}")
        }
    }

    private suspend fun requireIncoming(transferId: String): OneShotTransfer {
        val transfer = store.find(transferId) ?: throw IllegalArgumentException("No transfer $transferId")
        require(transfer.direction is OneShotTransfer.Direction.Incoming) { "Transfer $transferId is not incoming" }
        return transfer
    }

    private suspend fun ownOutgoing(peer: PeerIdentity, transferId: String): OneShotTransfer? {
        val transfer = store.find(transferId)

        if (transfer == null ||
            transfer.direction !is OneShotTransfer.Direction.Outgoing ||
            transfer.peer.deviceId != peer.deviceId
        ) {
            Timber.w("Device ${peer.deviceId} answered for transfer $transferId, which is not its to answer")
            return null
        }

        return transfer
    }

    private suspend fun peerName(deviceId: String): String =
        storage.trust.findByDeviceId(deviceId).firstOrNull()?.displayName ?: deviceId

    /** Bounded and consistent, or the whole offer is refused: nothing a peer sends is taken on trust. */
    private fun validated(files: List<OneShotFileDto>): List<OneShotTransferFile> {
        require(files.size in 1..MaxFiles) { "${files.size} files" }
        require(files.map { it.index }.sorted() == files.indices.toList()) { "Indices are not 0 until ${files.size}" }

        return files.sortedBy { it.index }.map {
            require(it.name.length <= MaxNameLength) { "Name of #${it.index} too long" }

            OneShotTransferFile(
                index = it.index,
                name = it.name,
                size = it.size,
                locator = null,
            )
        }
    }

    private companion object {
        const val MaxFiles = 1000
        const val MaxNameLength = 1000
        const val CancelledReason = "Cancelled by user"
    }
}
