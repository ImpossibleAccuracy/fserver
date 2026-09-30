package com.fserver.core.sync.server.handler.upload

import com.fserver.common.exception.SyncException
import com.fserver.core.network.dictionary.dto.UploadKey
import com.fserver.core.store.FServerStorage
import com.fserver.core.store.sync.FileIndexStore
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.limits.FileBudget
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.acceptsPeerWrites
import com.fserver.core.sync.transfer.RequestedDownloads
import com.fserver.files.upload.FileRecord

/** Whether this device takes a file a peer pushes: first the source's mode, then its file limits. */
internal class UploadAdmission(
    private val storage: FServerStorage,
    private val requestedDownloads: RequestedDownloads,
) {
    /** Throws when the mode takes no writes from [deviceId], unless we asked for the file. */
    fun checkMode(source: SourceEntry, deviceId: String, key: IndexedFileKey) {
        if (!source.acceptsPeerWrites && !requestedDownloads.isRequested(deviceId, key)) {
            throw SyncException.ModeForbiddenException(
                "Source ${source.id} takes no files from $deviceId under ${source.syncMode.type}"
            )
        }
    }

    /**
     * Our own limits, never the sender's. A file we hold is capped by its growth, a new one by
     * count and size; uploads still open on [uploads] count as booked.
     */
    suspend fun fitsLimits(source: SourceEntry, file: FileRecord, uploads: SessionUploads): Boolean {
        val limits = source.preferences.fileLimits
        if (limits == SourceEntry.Preferences.FileLimits.None) return true

        val key = IndexedFileKey(fileId = file.id.value, sourceId = source.id)
        val budget = FileBudget(limits, storage.index.presentUsage(source.id))

        // At most MaxConcurrentUploads of them, so a lookup each is cheap.
        for (upload in uploads.inFlight.values) {
            val other = (upload.key as? UploadKey.Source)?.toIndexed() ?: continue
            if (other.sourceId == source.id && other != key) budget.admit(other, upload.landing.size)
        }

        return budget.admit(key, file.metadata.size)
    }

    private suspend fun FileBudget.admit(key: IndexedFileKey, size: Long): Boolean {
        val held = storage.index.presentSize(key)
        return if (held != null) admitUpdate(from = held, to = size) else admitNew(size)
    }

    /** Size of the file we hold under [key], or null when we hold none. */
    private suspend fun FileIndexStore.presentSize(key: IndexedFileKey): Long? =
        findFile(key)?.takeIf { it.state is LocalIndexedFile.State.Present }?.size?.bytes
}
