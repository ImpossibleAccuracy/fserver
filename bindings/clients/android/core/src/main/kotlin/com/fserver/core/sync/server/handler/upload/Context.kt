package com.fserver.core.sync.server.handler.upload

import com.fserver.common.exception.TransferException
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.progress.SyncProgressReporter
import com.fserver.core.sync.server.SessionContext
import com.fserver.core.sync.server.SessionContext.Companion.MaxConcurrentUploads
import com.fserver.core.sync.server.SessionContext.Companion.UploadTimeout
import com.fserver.files.fs.FileSystem
import com.fserver.files.upload.FileRecord
import timber.log.Timber
import kotlin.time.Instant


/** Begins an upload, or resumes the one [file] has staged. */
internal suspend fun SessionContext.start(
    source: SourceEntry,
    file: FileRecord,
    deviceId: String,
    fs: FileSystem,
    staging: UploadStaging,
    startedAt: Instant,
    progress: SyncProgressReporter,
): UploadContext {
    val key = IndexedFileKey(fileId = file.id.value, sourceId = source.id)

    if (!uploads.containsKey(key) && uploads.size >= MaxConcurrentUploads) {
        throw TransferException.TooManyUploadsException(MaxConcurrentUploads)
    }

    // A second Init for the same file: park the first attempt, then recall it like any other.
    uploads.remove(key)?.let { park(it, staging) }

    val opened = staging.open(key, deviceId, file)

    val started = UploadContext(
        file = file,
        key = key,
        fs = fs,
        staging = opened.file,
        out = opened.file.openWriter(),
        committed = opened.committed,
        startedAt = startedAt,
        buffered = buffered,
        progress = progress,
        scope = scope,
    )

    uploads[key] = started

    return started
}

internal suspend fun SessionContext.pruneStaleUploads(now: Instant, staging: UploadStaging) {
    val stale = uploads.entries
        .filter { now - it.value.startedAt > UploadTimeout }
        .map { it.key to it.value }

    for ((key, upload) in stale) {
        if (uploads.remove(key, upload)) park(upload, staging)
    }
}

/** Parks every upload still open; the session feeding them is gone. */
internal suspend fun SessionContext.parkAll(staging: UploadStaging) {
    val open = uploads.values.toList()
    uploads.clear()
    open.forEach { park(it, staging) }
}

/** Stops [upload] and flushes what it wrote, so the peer resumes from there. */
internal suspend fun park(upload: UploadContext, staging: UploadStaging) {
    val flushed = runCatchingCancellable { upload.flush() }
    upload.close()

    flushed.mapCatching { staging.checkpoint(upload.key, it) }
        .onFailure { Timber.w(it, "Cannot checkpoint the upload of ${upload.file.path}") }
}
