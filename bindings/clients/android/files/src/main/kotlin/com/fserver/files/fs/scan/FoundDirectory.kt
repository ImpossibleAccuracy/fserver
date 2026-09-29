package com.fserver.files.fs.scan

/** One directory a tree scan walked through, empty or not. */
data class FoundDirectory(
    /** Source-relative and canonical, like [FoundFile.path]. */
    val path: String,
    /** Address of the directory in the source. */
    val locator: String,
)
