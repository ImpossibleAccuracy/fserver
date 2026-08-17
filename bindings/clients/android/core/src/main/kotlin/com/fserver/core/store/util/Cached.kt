package com.fserver.core.store.util

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock


/** Simple cache for a value that is loaded asynchronously. */
class Cached<T>(
    private val loader: suspend () -> T,
) {
    private var cached: T? = null
    private val lock = Mutex()

    /** Loads the value, caching it for future calls. */
    suspend fun load(): T = cached ?: lock.withLock {
        cached ?: loader().also { cached = it }
    }

    /** Mark cache as invalid, so the next call to [load] will reload it. */
    fun invalidate() {
        cached = null
    }
}
