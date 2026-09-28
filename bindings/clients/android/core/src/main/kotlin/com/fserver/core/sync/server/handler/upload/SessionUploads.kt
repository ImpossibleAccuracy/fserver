package com.fserver.core.sync.server.handler.upload

import com.fserver.common.exception.TransferException
import com.fserver.common.utils.StageTimer
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.progress.impl.SyncProgressReporter
import com.fserver.files.fs.FileSystem
import com.fserver.files.upload.FileRecord
import kotlinx.coroutines.CoroutineScope
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * The uploads one peer session has open: the upload part of its
 * [com.fserver.core.sync.server.SessionContext]. [scope] is the session's, so every writer dies with it.
 */
internal class SessionUploads(
    private val scope: CoroutineScope,
    private val staging: UploadStaging,
    private val progress: SyncProgressReporter,
) {
    val inFlight: MutableMap<IndexedFileKey, UploadContext> = ConcurrentHashMap()

    /** One buffer for the whole session, so a peer cannot multiply it by opening more uploads. */
    val buffered = AtomicInteger(0)

    val collector = StageTimer("session-collector")

    /** Begins an upload, or resumes the one [file] has staged. */
    suspend fun start(
        source: SourceEntry,
        file: FileRecord,
        deviceId: String,
        fs: FileSystem,
        startedAt: Instant,
    ): UploadContext {
        val key = IndexedFileKey(fileId = file.id.value, sourceId = source.id)

        if (!inFlight.containsKey(key) && inFlight.size >= MaxConcurrentUploads) {
            throw TransferException.TooManyUploadsException(MaxConcurrentUploads)
        }

        // A second Init for the same file: park the first attempt, then recall it like any other.
        inFlight.remove(key)?.let { park(it) }

        val opened = staging.open(key, deviceId, file)

        val started = UploadContext(
            file = file,
            key = key,
            fs = fs,
            staging = opened.file,
            out = opened.file.openWriter(),
            committed = opened.committed,
            startedAt = startedAt,
            buffered = buffered,
            progress = progress,
            scope = scope,
        )

        inFlight[key] = started

        return started
    }

    /** Nothing else parks an upload whose sender stopped mid-stream. */
    suspend fun pruneStale(now: Instant) {
        val stale = inFlight.entries
            .filter { now - it.value.startedAt > UploadTimeout }
            .map { it.key to it.value }

        for ((key, upload) in stale) {
            if (inFlight.remove(key, upload)) park(upload)
        }
    }

    /** Parks every upload still open; the session feeding them is gone. */
    suspend fun parkAll() {
        val open = inFlight.values.toList()
        inFlight.clear()
        open.forEach { park(it) }
    }

    /** Stops [upload] and flushes what it wrote, so the peer resumes from there. */
    suspend fun park(upload: UploadContext) {
        val flushed = runCatchingCancellable { upload.flush() }
        upload.close()

        flushed.mapCatching { staging.checkpoint(upload.key, it) }
            .onFailure { Timber.w(it, "Cannot checkpoint the upload of ${upload.file.path}") }
    }

    companion object {
        /** Uploads one peer may have open at once. Each holds a writer and a file of its own. */
        const val MaxConcurrentUploads = 8

        /** Chunk bytes the session holds in memory while they wait for the disk. */
        const val InFlightChunkBytesLimit = 50 * 1024 * 1024 // 50 MiB TODO: move to config

        val UploadTimeout = 30.minutes
    }
}
