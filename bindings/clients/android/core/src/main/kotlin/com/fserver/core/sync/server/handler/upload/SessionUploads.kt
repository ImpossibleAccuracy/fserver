package com.fserver.core.sync.server.handler.upload

import com.fserver.common.exception.TransferException
import com.fserver.common.utils.StageTimer
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.network.dictionary.dto.UploadKey
import com.fserver.core.sync.progress.impl.SyncProgressReporter
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
    private val progress: SyncProgressReporter,
) {
    val inFlight: MutableMap<UploadKey, UploadContext> = ConcurrentHashMap()

    /** One buffer for the whole session, so a peer cannot multiply it by opening more uploads. */
    val buffered = AtomicInteger(0)

    val collector = StageTimer("session-collector")

    /**
     * Makes room for [key] before its target opens staging: refused past [MaxConcurrentUploads],
     * and a second Init for the same file parks the first attempt, so the target recalls it like any other.
     */
    suspend fun reserve(key: UploadKey) {
        if (!inFlight.containsKey(key) && inFlight.size >= MaxConcurrentUploads) {
            throw TransferException.TooManyUploadsException(MaxConcurrentUploads)
        }

        inFlight.remove(key)?.let { park(it) }
    }

    /** Begins an upload into what its target [staged], resuming from what that holds. */
    suspend fun start(
        key: UploadKey,
        staged: UploadTarget.Opening.Staged,
        startedAt: Instant,
    ): UploadContext {
        val started = UploadContext(
            key = key,
            landing = staged.landing,
            staging = staged.staging,
            out = staged.staging.openWriter(),
            committed = staged.committed,
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

        flushed.mapCatching { upload.landing.checkpoint(it) }
            .onFailure { Timber.w(it, "Cannot checkpoint the upload of ${upload.landing.path}") }
    }

    companion object {
        /** Uploads one peer may have open at once. Each holds a writer and a file of its own. */
        const val MaxConcurrentUploads = 8

        /** Chunk bytes the session holds in memory while they wait for the disk. */
        const val InFlightChunkBytesLimit = 50 * 1024 * 1024 // 50 MiB TODO: move to config

        val UploadTimeout = 30.minutes
    }
}
