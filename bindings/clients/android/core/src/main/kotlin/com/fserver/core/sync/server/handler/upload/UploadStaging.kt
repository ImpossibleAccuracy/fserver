package com.fserver.core.sync.server.handler.upload

import com.fserver.common.exception.FileSystemException
import com.fserver.core.crypto.internal.SealedFiles
import com.fserver.core.crypto.internal.SealedFsFile
import com.fserver.core.crypto.model.EncryptionPolicy
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.model.StagedUpload
import com.fserver.core.sync.server.handler.upload.UploadStaging.Companion.DataFile
import com.fserver.core.util.TimeProvider
import com.fserver.files.FilesNode
import com.fserver.files.fs.FsFile
import com.fserver.files.upload.FileRecord
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Where a peer's push waits until it is whole: `{sourceId}/{fileId}/data` in [FilesNode.openStaging],
 * with a [StagedUpload] row per file. Rows are what lets resume outlive the session and the process.
 *
 * For a source under `Required` the bytes are sealed from the first chunk on: a plaintext copy in
 * staging would defeat encrypting the source (Storage Encryption §6.4).
 */
internal class UploadStaging(
    private val storage: FServerStorage,
    private val node: FilesNode,
    private val timeProvider: TimeProvider,
    private val sealedFiles: SealedFiles,
) {
    private val staging get() = node.openStaging()

    class Opened(val file: FsFile, val committed: Long)

    /**
     * The staged bytes of [file] pushed by [deviceId]: the ones parked by an earlier attempt when
     * they are for the same version, a fresh empty file otherwise.
     */
    suspend fun open(key: IndexedFileKey, deviceId: String, file: FileRecord): Opened {
        // Both ids are peer-chosen and become directory names.
        requireSegment(key.sourceId)
        requireSegment(key.fileId)

        val now = timeProvider.now()

        storage.uploads.find(key)?.let { parked ->
            val staged = if (parked.deviceId == deviceId && parked.isFor(file)) {
                openOrNull(parked.locator)
            } else {
                null
            }

            if (staged != null) {
                storage.uploads.checkpoint(key, parked.committedOffset, now)
                return Opened(staged, parked.committedOffset)
            }

            discard(key, parked.locator)
        }

        val staged = seal(key.sourceId, create(pathOf(key)))

        storage.uploads.upsert(
            StagedUpload(
                sourceId = key.sourceId,
                fileId = key.fileId,
                deviceId = deviceId,
                locator = staged.locator,
                size = file.metadata.size,
                modifiedAt = file.metadata.lastModified,
                versionHlc = file.metadata.version?.hlc,
                versionOrigin = file.metadata.version?.originDevice,
                committedOffset = 0,
                startedAt = now,
                touchedAt = now,
            )
        )

        return Opened(staged, committed = 0)
    }

    /** Records that `[0, offset)` is flushed and survives a crash. */
    suspend fun checkpoint(key: IndexedFileKey, offset: Long) {
        storage.uploads.checkpoint(key, offset, timeProvider.now())
    }

    /** Forgets the upload; the bytes go too when [locator] still has any. */
    suspend fun discard(key: IndexedFileKey, locator: String?) {
        locator?.let { openOrNull(it) }?.delete()
        storage.uploads.delete(key)
    }

    /**
     * A fresh file at [path]. One already there has no row - [open] found none - so it is an
     * orphan GC has not reached yet.
     */
    private suspend fun create(path: String): FsFile =
        try {
            staging.createFile(path)
        } catch (_: FileSystemException.AlreadyExists) {
            staging.scan().result().getOrThrow()
                .find { it.path == path }
                ?.let { openOrNull(it.locator) }
                ?.delete()

            staging.createFile(path)
        }

    /** [raw] as a sealed file when its source asks for one, still empty. */
    private suspend fun seal(sourceId: String, raw: FsFile): FsFile {
        val policy =
            storage.sources.findById(sourceId)?.preferences?.encryption as? EncryptionPolicy.Required
                ?: return raw

        val sealed = sealedFiles.create(sourceId, policy.cipherId)
        try {
            raw.openWriter().use {
                sealed.initialize(it)
                it.sync()
            }
        } catch (e: Throwable) {
            withContext(NonCancellable) { raw.delete() }
            throw e
        }
        return SealedFsFile(raw, sealed.header, sealedFiles)
    }

    /** Sealed or not, as its own header says: the source's policy may have changed since. */
    private suspend fun openOrNull(locator: String): FsFile? {
        val raw = try {
            staging.openFile(locator)
        } catch (_: FileSystemException.InvalidPath) {
            null
        } ?: return null

        // Plaintext that merely starts like a header stays plaintext.
        val header = try {
            raw.openReader().use { sealedFiles.headerOf(it) }
        } catch (_: FileSystemException.Corrupted) {
            null
        }
        return if (header != null) SealedFsFile(raw, header, sealedFiles) else raw
    }

    companion object {
        const val DataFile = "data"
    }
}

private fun StagedUpload.isFor(file: FileRecord): Boolean =
    size == file.metadata.size &&
            versionHlc == file.metadata.version?.hlc &&
            versionOrigin == file.metadata.version?.originDevice

/** Throws if [id] is is invalid as a directory name. */
private fun requireSegment(id: String) {
    if (id.isEmpty() || id == "." || id == ".." || id.any { it == '/' || it == '\\' }) {
        throw FileSystemException.InvalidPath(id)
    }
}

private fun pathOf(key: IndexedFileKey) = "${key.sourceId}/${key.fileId}/$DataFile"
