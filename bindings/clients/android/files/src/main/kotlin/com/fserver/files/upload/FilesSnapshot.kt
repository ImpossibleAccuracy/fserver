package com.fserver.files.upload

/**
 * What both sides hold at one moment, as seen by the caller.
 *
 * Point-in-time and already collected: a strategy gets no way to ask for more,
 * which is what keeps it free of network and filesystem access.
 */
data class FilesSnapshot(
    val local: List<FileRecord>,
    val remote: List<FileRecord>,
) {
    val localById: Map<FileId, FileRecord> by lazy { local.associateBy(FileRecord::id) }
    val remoteById: Map<FileId, FileRecord> by lazy { remote.associateBy(FileRecord::id) }

    /**
     * Both sides joined by [FileId], one entry per file known to either side. This is the shape
     * every strategy actually wants, so it is here rather than re-derived in each of them.
     */
    fun join(): List<Join> = (localById.keys + remoteById.keys).map { id ->
        Join(id, localById[id], remoteById[id])
    }

    /** At least one of [local] / [remote] is non-null. */
    data class Join(
        val id: FileId,
        val local: FileRecord?,
        val remote: FileRecord?,
    )
}
