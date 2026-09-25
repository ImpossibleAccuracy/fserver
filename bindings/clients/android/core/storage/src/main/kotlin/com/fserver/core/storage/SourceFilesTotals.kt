package com.fserver.core.storage

/**
 * One source's files by where they stand. [pending] is what was indexed after the last completed
 * pass; [matched] is held on both sides, going by the peer's last reported index.
 */
data class SourceFilesTotals(
    val here: FilesTotal,
    val pending: FilesTotal,
    val evicted: FilesTotal,
    val peer: FilesTotal,
    val matched: FilesTotal,
)
