package com.fserver.core.sync.progress

/** Identity of one transfer. The same file moving both ways is two of them. */
data class FileTransferKey(
    val direction: FileTransfer.Direction,
    val sourceId: String,
    val fileId: String,
) {
    /** Stable across passes, so a list keyed by it does not re-animate every update. */
    val id: String get() = "$direction/$sourceId/$fileId"
}