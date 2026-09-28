package com.fserver.core.sync.progress

/** Identity of one transfer. The same file moving both ways is two of them. */
data class FileTransferKey(
    val direction: FileTransfer.Direction,
    val sourceId: String,
    val fileId: String,
) {
    /** Stable across passes, so a list keyed by it does not re-animate every update. */
    val id: String get() = "$direction/$sourceId/$fileId"

    internal companion object {
        /** Sending side of a transfer this device drives. */
        fun outgoing(sourceId: String, fileId: String) =
            FileTransferKey(FileTransfer.Direction.Outgoing, sourceId, fileId)

        /** Receiving side, whether we asked for the file or the peer pushed it. */
        fun incoming(sourceId: String, fileId: String) =
            FileTransferKey(FileTransfer.Direction.Incoming, sourceId, fileId)
    }
}
