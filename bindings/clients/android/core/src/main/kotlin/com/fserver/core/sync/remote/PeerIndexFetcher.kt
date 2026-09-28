package com.fserver.core.sync.remote

import com.fserver.common.exception.SyncException
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.dto.FileRecordDto
import com.fserver.core.network.dictionary.dto.latestHlc
import com.fserver.core.network.dictionary.dto.toFileRecord
import com.fserver.core.network.dictionary.dto.toRemoteIndexed
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.version.HybridLogicalClock
import com.fserver.core.util.TimeProvider
import com.fserver.files.upload.FileRecord
import timber.log.Timber

/** The peer's index: fetched for a pass, and cached in `remoteIndex` for what comes after it. */
internal class PeerIndexFetcher(
    private val storage: FServerStorage,
    private val connector: PeerConnector,
    private val timeProvider: TimeProvider,
    private val clock: HybridLogicalClock,
) {
    suspend fun fetchIndex(source: SourceEntry): List<FileRecord> {
        val device = connector.connectToDevice(source)

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

    /**
     * The peer holds [file] now, as we sent it; recorded so it shows before its next published
     * index. A cache write, so a failure is logged rather than failing a finished upload.
     */
    suspend fun recordSent(source: SourceEntry, file: FileRecordDto) {
        runCatchingCancellable {
            storage.remoteIndex.upsert(deviceId = source.deviceId, file = file.toRemoteIndexed(timeProvider.now()))
        }.onFailure { Timber.w(it, "Failed to record upload of ${file.id} in remote index") }
    }
}
