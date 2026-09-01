package com.fserver.files.fs

import com.fserver.common.model.FileSize
import kotlin.time.Instant

data class FoundFile(
    /**
     * Source-relative and canonical - see [com.fserver.common.utils.SourcePaths]. The only field a
     * peer ever sees, and the one file identity is derived from.
     */
    val path: String,
    /** Address of the file in the source. */
    val locator: String,
    val size: FileSize,
    val lastModified: Instant,
)
