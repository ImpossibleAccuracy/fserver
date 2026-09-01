package com.fserver.files.scan

import com.fserver.common.model.FileSize
import kotlin.time.Instant

data class FoundFile(
    /**
     * Source-relative and canonical - see [com.fserver.common.utils.SourcePaths]. The only field a
     * peer ever sees, and the one file identity is derived from.
     */
    val path: String,
    val size: FileSize,
    val lastModified: Instant,
    val provider: ContentProvider,
) {
    /** Filesystem specific provider of the file's content. */
    interface ContentProvider {
        /** Address of the file in the source. */
        val locator: String

        /** Load the file's bytes. */
        suspend fun loadBytes(): ByteArray
    }
}
