package com.fserver.core.sync.server

import com.fserver.common.utils.StageTimer
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.server.handler.upload.UploadContext
import kotlinx.coroutines.CoroutineScope
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.minutes

/**
 * Per-session state. Owned by the coroutine serving that session, so a reconnect starts clean.
 *
 * [scope] is that coroutine's, so every writer started here dies with the session feeding it.
 */
internal class SessionContext(val scope: CoroutineScope) {
    val uploads: MutableMap<IndexedFileKey, UploadContext> = ConcurrentHashMap()

    /** One buffer for the whole session, so a peer cannot multiply it by opening more uploads. */
    val buffered = AtomicInteger(0)

    val collector = StageTimer("session-collector")

    companion object {
        /** Uploads one peer may have open at once. Each holds a writer and a file of its own. */
        const val MaxConcurrentUploads = 8

        /** Chunk bytes the session holds in memory while they wait for the disk. */
        const val InFlightChunkBytesLimit = 50 * 1024 * 1024 // 50 MiB TODO: move to config

        val UploadTimeout = 30.minutes
    }
}

