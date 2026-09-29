package com.fserver.files.fs.scan

/** What [com.fserver.files.fs.FileSystem.scanTree] found: the files, plus every directory - empty ones included. */
data class ScanTree(
    val files: List<FoundFile>,
    val directories: List<FoundDirectory>,
)
