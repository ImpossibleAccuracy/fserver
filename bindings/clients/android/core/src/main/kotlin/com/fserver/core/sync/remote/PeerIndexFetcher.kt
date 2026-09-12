package com.fserver.core.sync.remote

import com.fserver.common.exception.SyncException
import com.fserver.core.network.NetworkController
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.dto.toFileRecord
import com.fserver.core.network.dictionary.dto.toRemoteIndexed
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.util.TimeProvider
import com.fserver.files.upload.FileRecord
import com.fserver.net.session.PeerSession

internal class PeerIndexFetcher(
    private val storage: FServerStorage,
    private val networkController: NetworkController,
    private val devicesRepository: DevicesRepository,
    private val timeProvider: TimeProvider,
) {
    suspend fun fetchIndex(source: SourceEntry): List<FileRecord> {
        val device = connectToDevice(source)

        val response = device.request(FileServerMessages.FetchFiles.Request(source.id))
            .getOrThrow()

        return when (response) {
            is FileServerMessages.FetchFiles.FilesList -> {
                // Written through rather than read back: the pass plans on the answer it just got,
                // and the cache is what a later change - or a restart - starts from.
                val seenAt = timeProvider.now()
                storage.remoteIndex.replace(
                    sourceId = source.id,
                    deviceId = source.deviceId,
                    files = response.files.map { it.toRemoteIndexed(seenAt) },
                )

                response.files.map { it.toFileRecord() }
            }

            is FileServerMessages.FetchFiles.Failed -> throw SyncException.RemoteRejectedException(
                "Device ${source.deviceId} would not list source ${source.id}: ${response.reason}"
            )

            else -> throw IllegalStateException("Unexpected response from device ${source.deviceId}: $response")
        }
    }

    suspend fun connectToDevice(source: SourceEntry): PeerSession<FileServerMessages> =
        connectToDevice(source.deviceId)

    suspend fun connectToDevice(deviceId: String): PeerSession<FileServerMessages> =
        networkController.incomingConnections.session(deviceId)
            ?: tryToConnectByDeviceId(deviceId)

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
