package com.fserver.core.sync.limits

/** How many of a source's files this device holds ([com.fserver.core.sync.index.LocalIndexedFile.State.Present]), and their total size. */
data class SourceUsage(
    val files: Int,
    val bytes: Long,
)