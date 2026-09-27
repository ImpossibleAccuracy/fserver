package com.fserver.core.sync.runner

import com.fserver.core.network.dictionary.RemoteOperation
import com.fserver.core.network.dictionary.dto.VersionDto
import com.fserver.core.network.utils.runRemoteOperation
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.remote.PeerIndexFetcher
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Asks the source's peer to push one file back to us. Returns once it is written and indexed here:
 * the peer confirms only after our side accepted the whole upload.
 */
internal class FileDownloader(
    private val remoteFetcher: PeerIndexFetcher,
) {
    /** [version] is what the file is recorded as here; null takes the peer's own. */
    suspend fun download(
        source: SourceEntry,
        key: IndexedFileKey,
        sizeBytes: Long,
        version: VersionDto? = null,
    ) {
        val session = remoteFetcher.connectToDevice(source)

        session.runRemoteOperation(
            operation = RemoteOperation.File.Download(key = key, version = version),
            timeout = downloadTimeout(sizeBytes),
        )
    }

    /**
     * The peer confirms a download only once the whole file is across, so a fixed timeout fails
     * big transfers that are working fine. Budgeted from size against a pessimistic link instead.
     */
    private fun downloadTimeout(sizeBytes: Long): Duration =
        (MinDownloadTimeout + (sizeBytes / SlowestExpectedBytesPerSecond).seconds)
            .coerceAtMost(MaxDownloadTimeout)

    companion object {
        // TODO: make configurable
        private val MinDownloadTimeout = 2.minutes
        private val MaxDownloadTimeout = 2.hours
        private const val SlowestExpectedBytesPerSecond = 64L * 1024
    }
}
