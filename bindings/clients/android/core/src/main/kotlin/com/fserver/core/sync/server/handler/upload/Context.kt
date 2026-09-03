package com.fserver.core.sync.server.handler.upload

import com.fserver.common.exception.TransferException
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.server.SessionContext
import com.fserver.core.sync.server.SessionContext.Companion.MaxConcurrentUploads
import com.fserver.core.sync.server.SessionContext.Companion.UploadTimeout
import com.fserver.files.fs.FileSystem
import com.fserver.files.upload.FileRecord
import kotlin.time.Instant


/** Begins an upload, replacing whatever the same file had in flight before. */
internal suspend fun SessionContext.start(
    key: IndexedFileKey,
    file: FileRecord,
    fs: FileSystem,
    startedAt: Instant,
) {
    if (!uploads.containsKey(key) && uploads.size >= MaxConcurrentUploads) {
        throw TransferException.TooManyUploadsException(MaxConcurrentUploads)
    }

    val started = UploadContext(
        file = file,
        startedAt = startedAt,
        fs = fs,
        buffered = buffered,
        scope = scope,
    )

    // A second Init for the same file leaves the first one's bytes half written.
    uploads.put(key, started)?.abandon()
}

internal suspend fun SessionContext.pruneStaleUploads(now: Instant) {
    val stale = uploads.entries
        .filter { now - it.value.startedAt > UploadTimeout }
        .map { it.key to it.value }

    for ((key, upload) in stale) {
        if (uploads.remove(key, upload)) upload.abandon()
    }
}
