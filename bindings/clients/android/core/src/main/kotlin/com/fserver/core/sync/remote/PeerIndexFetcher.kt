package com.fserver.core.sync.remote

import com.fserver.common.exception.SyncException
import com.fserver.core.network.NetworkController
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.device.impl.ReachabilityTracker
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.dto.toFileRecord
import com.fserver.core.network.dictionary.dto.latestHlc
import com.fserver.core.network.dictionary.dto.toRemoteIndexed
import com.fserver.core.network.info.model.PeerLocator
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.version.HybridLogicalClock
import com.fserver.core.util.TimeProvider
import com.fserver.files.upload.FileRecord
import com.fserver.net.session.PeerSession

internal class PeerIndexFetcher(
    private val storage: FServerStorage,
    private val networkController: NetworkController,
    private val devicesRepository: DevicesRepository,
    private val reachability: ReachabilityTracker,
    private val timeProvider: TimeProvider,
    private val clock: HybridLogicalClock,
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

                response.files.latestHlc()?.let { clock.receive(it) }

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

    /**
     * The one place a pass turns a device id into a session, so it is also where the pass records
     * whether the device could be reached at all - a failure here is what the user is shown
     * instead of a source that silently stays as it was.
     */
    suspend fun connectToDevice(deviceId: String): PeerSession<FileServerMessages> {
        networkController.incomingConnections.session(deviceId)?.let { session ->
            return session
        }

        return tryToConnectByDeviceId(deviceId)
    }

    private suspend fun tryToConnectByDeviceId(deviceId: String): PeerSession<FileServerMessages> {
        val peer = PeerLocator.KnownDevice(deviceId)

        devicesRepository.connect(peer, null).getOrThrow()

        return networkController.incomingConnections.session(deviceId)
            ?: throw IllegalStateException("Failed to establish session with device $deviceId")
    }
}
