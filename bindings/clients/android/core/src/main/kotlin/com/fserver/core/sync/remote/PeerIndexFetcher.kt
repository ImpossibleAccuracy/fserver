package com.fserver.core.sync.remote

import com.fserver.core.network.NetworkController
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.SourceEntry
import com.fserver.files.upload.FileRecord
import com.fserver.net.session.PeerSession

internal class PeerIndexFetcher(
    private val storage: FServerStorage,
    private val networkController: NetworkController,
    private val devicesRepository: DevicesRepository,
) {
    suspend fun fetchIndex(source: SourceEntry): List<FileRecord> {
        val device = networkController.incomingConnections.session(source.deviceId)
            ?: tryToConnectByDeviceId(source.deviceId)

        val response = device.request(FileServerMessages.Request.SavedFiles)
            .getOrThrow()

        if (response !is FileServerMessages.Response.SavedFiles) {
            throw IllegalStateException("Unexpected response from device ${source.deviceId}: $response")
        }

        // TODO
        return emptyList<FileRecord>()
    }

    private suspend fun tryToConnectByDeviceId(deviceId: String): PeerSession<FileServerMessages> {
        val known = storage.trust.findKnownRoute(deviceId)
            ?: throw IllegalStateException("No known route to device $deviceId")

        val peer = known.asPeerLocator()
            ?: throw IllegalStateException("Saved route to device $deviceId doesn't allow to connect")

        devicesRepository.connect(peer, null)
            .getOrThrow()

        return networkController.incomingConnections.session(deviceId)
            ?: throw IllegalStateException("Failed to establish session with device $deviceId")
    }
}
