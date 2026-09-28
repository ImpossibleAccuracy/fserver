package com.fserver.core.sync.transfer

import com.fserver.core.sync.index.IndexedFileKey
import java.util.concurrent.ConcurrentHashMap

/**
 * Downloads this device asked for and still waits on. A download arrives as the peer's upload, so
 * a source that refuses peer writes accepts an upload only as the answer to one of these.
 */
internal class RequestedDownloads {
    private val pending = ConcurrentHashMap<Request, Int>()

    suspend fun <T> awaiting(deviceId: String, key: IndexedFileKey, block: suspend () -> T): T {
        val request = Request(deviceId, key)
        pending.merge(request, 1, Int::plus)

        try {
            return block()
        } finally {
            pending.computeIfPresent(request) { _, count -> (count - 1).takeIf { it > 0 } }
        }
    }

    fun isRequested(deviceId: String, key: IndexedFileKey): Boolean = pending.containsKey(Request(deviceId, key))

    private data class Request(val deviceId: String, val key: IndexedFileKey)
}
