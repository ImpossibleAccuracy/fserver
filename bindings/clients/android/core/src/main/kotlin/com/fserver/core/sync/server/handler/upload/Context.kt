package com.fserver.core.sync.server.handler.upload

import com.fserver.common.exception.TransferException
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.progress.SyncProgressReporter
import com.fserver.core.sync.server.SessionContext
import com.fserver.core.sync.server.SessionContext.Companion.MaxConcurrentUploads
import com.fserver.core.sync.server.SessionContext.Companion.UploadTimeout
import com.fserver.files.fs.FileSystem
import com.fserver.files.upload.FileRecord
import kotlin.time.Instant


/** Begins an upload, replacing whatever the same file had in flight before. */
internal suspend fun SessionContext.start(
    source: SourceEntry,
    file: FileRecord,
    fs: FileSystem,
    startedAt: Instant,
    progress: SyncProgressReporter,
): UploadContext {
    val key = IndexedFileKey(fileId = file.id.value, sourceId = source.id)

    if (!uploads.containsKey(key) && uploads.size >= MaxConcurrentUploads) {
        throw TransferException.TooManyUploadsException(MaxConcurrentUploads)
    }

    val downloadPath = if (fs.fileExists(file.path)) {
        // atomic write to a temp file, then rename to the real path when done
        // TODO: collides with a user's own "<name>.temp" and with leftovers of a crashed upload;
        //  needs a reserved unique name the scanner skips
        "${file.path}.temp"
    } else {
        file.path
    }

    val started = UploadContext(
        file = file,
        downloadPath = downloadPath,
        startedAt = startedAt,
        key = key,
        fs = fs,
        buffered = buffered,
        progress = progress,
        scope = scope,
    )

    // A second Init for the same file leaves the first one's bytes half written.
    uploads.put(key, started)?.abandon()

    return started
}

internal suspend fun SessionContext.pruneStaleUploads(now: Instant) {
    val stale = uploads.entries
        .filter { now - it.value.startedAt > UploadTimeout }
        .map { it.key to it.value }

    for ((key, upload) in stale) {
        if (uploads.remove(key, upload)) upload.abandon()
    }
}
