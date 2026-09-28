package com.fserver.core.sync.index

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * One lock per source over its local index rows. Every read-modify-write of those rows holds it:
 * a scan and a write landing in between interleave into double-bumped version vectors.
 */
internal class SourceIndexLocks {
    private val locks = ConcurrentHashMap<String, Mutex>()

    suspend fun <T> withLock(sourceId: String, block: suspend () -> T): T =
        locks.computeIfAbsent(sourceId) { Mutex() }.withLock { block() }
}
