package com.fserver.core.sync.remote

import com.fserver.core.journal.impl.JournalWriter
import com.fserver.core.network.NetworkController
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.info.model.PeerLocator
import com.fserver.core.sync.model.SourceEntry
import com.fserver.net.session.PeerSession

/**
 * The one place sync turns a device id into a session: the open one, or a fresh dial. A dial
 * records whether the device could be reached, so a failure here is what the user is shown.
 */
internal class PeerConnector(
    private val networkController: NetworkController,
    private val devicesRepository: DevicesRepository,
    private val journal: JournalWriter,
) {
    suspend fun connectToDevice(source: SourceEntry): PeerSession<FileServerMessages> =
        connectToDevice(source.deviceId)

    suspend fun connectToDevice(deviceId: String): PeerSession<FileServerMessages> {
        networkController.incomingConnections.session(deviceId)?.let { return it }

        devicesRepository.connect(PeerLocator.KnownDevice(deviceId), null)
            .onSuccess { journal.connected(deviceId) }
            .onFailure { journal.connectFailed(deviceId, it) }
            .getOrThrow()

        return networkController.incomingConnections.session(deviceId)
            ?: throw IllegalStateException("Failed to establish session with device $deviceId")
    }
}
