package com.fserver.core.files.scan

import com.fserver.common.model.FileSize
import kotlin.time.Instant

/** What a scan turned up. [directories] lists every folder walked, empty ones included. */
data class ScannedContent(
    val files: List<File>,
    val directories: List<Directory>,
) {
    /** One directory a scan walked through. Only a whole-device or single-directory scan reports these. */
    data class Directory(
        /** Source-relative and canonical - see [com.fserver.common.utils.SourcePaths]. */
        val path: String,
        /** Address of the directory in the source. */
        val locator: String,
    )

    /** One file a scan turned up, as a UI wants to list it. */
    data class File(
        /** Source-relative and canonical - see [com.fserver.common.utils.SourcePaths]. */
        val path: String,
        /** Parent of [path], or empty at the source root. */
        val directory: String,
        /** Address of the file in the source. Mostly file path or URI. */
        val locator: String,
        val size: FileSize,
        val lastModified: Instant,
    )
}
