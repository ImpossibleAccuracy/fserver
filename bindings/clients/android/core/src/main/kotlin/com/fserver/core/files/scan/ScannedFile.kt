package com.fserver.core.files.scan

import com.fserver.common.model.FileSize
import kotlin.time.Instant

/** One file a scan turned up, as a UI wants to list it. */
data class ScannedFile(
    /** Source-relative and canonical - see [com.fserver.common.utils.SourcePaths]. */
    val path: String,
    /** Parent of [path], or empty at the source root. */
    val directory: String,
    val size: FileSize,
    val lastModified: Instant,
)
