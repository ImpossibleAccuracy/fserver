package com.fserver.core.files.preview

import com.fserver.common.model.ContentHash
import com.fserver.common.model.FileSize
import com.fserver.core.disk.StoreType
import java.io.InputStream
import kotlin.time.Instant

/**
 * Host hook called right before a file's local bytes are evicted, while they can still be read -
 * the last chance to keep a preview for the stub the user keeps seeing. Optional: see
 * [com.fserver.core.FServerConfig.evictionPreviewer].
 *
 * Best effort: a failure or timeout is logged and the eviction goes ahead without a preview.
 */
fun interface EvictionPreviewer {
    suspend fun capture(file: EvictingFile)

    /** Bytes the kept previews take now, by where they sit. Measured on each call. */
    suspend fun usage(): Map<StoreType, Long> = emptyMap()

    /** Drops every kept preview. Stubs show no picture until their file is fetched again. */
    suspend fun clear() {}
}

/** The file about to be evicted. [read] and [locator] are valid only during [EvictionPreviewer.capture]. */
class EvictingFile internal constructor(
    val sourceId: String,
    val fileId: String,
    val path: String,
    val size: FileSize,
    /** As the index has it, like [com.fserver.core.files.SyncFileEntry.modifiedAt]. */
    val modifiedAt: Instant,
    val hash: ContentHash,
    /** Where the file lives, in the backend's own terms - as [com.fserver.core.files.SyncFileEntry.locator]. */
    val locator: String,
    private val open: suspend () -> InputStream,
) {
    /** Opens the file's bytes. The caller closes the stream. */
    suspend fun read(): InputStream = open()
}
