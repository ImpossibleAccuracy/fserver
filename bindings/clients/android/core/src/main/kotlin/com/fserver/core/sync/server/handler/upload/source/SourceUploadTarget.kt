package com.fserver.core.sync.server.handler.upload.source

import com.fserver.common.model.ContentHash
import com.fserver.common.utils.StageTimer
import com.fserver.core.crypto.internal.SourceFileSystems
import com.fserver.core.crypto.internal.atRest
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.dto.UploadKey
import com.fserver.core.network.dictionary.dto.toFileRecord
import com.fserver.core.sync.index.LocalIndexWriter
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.server.SourceAuthorizer
import com.fserver.core.sync.server.handler.upload.SessionUploads
import com.fserver.core.sync.server.handler.upload.UploadAdmission
import com.fserver.core.sync.server.handler.upload.UploadLanding
import com.fserver.core.sync.server.handler.upload.UploadStaging
import com.fserver.core.sync.server.handler.upload.UploadTarget
import com.fserver.files.fs.FileSystem
import com.fserver.files.fs.FsFile
import com.fserver.files.upload.FileRecord
import com.fserver.net.security.identity.PeerIdentity
import timber.log.Timber

/**
 * A source's file: sent only by the device the source syncs with and only under a mode that takes
 * its writes, staged in [com.fserver.core.sync.server.handler.upload.UploadStaging], placed at the path the peer holds and indexed.
 */
internal class SourceUploadTarget(
    private val authorizer: SourceAuthorizer,
    private val admission: UploadAdmission,
    private val indexWriter: LocalIndexWriter,
    private val sourceFiles: SourceFileSystems,
    private val staging: UploadStaging,
) : UploadTarget {

    override suspend fun open(
        peer: PeerIdentity,
        init: FileServerMessages.Upload.Init,
        uploads: SessionUploads,
    ): UploadTarget.Opening {
        val key = init.key as UploadKey.Source
        val source = authorizer.authorizedSource(peer, key.sourceId)

        admission.checkMode(source, peer.deviceId, key.toIndexed())

        val file = requireNotNull(init.file) { "Init of $key describes no file" }.toFileRecord()
        require(file.id.value == key.fileId) { "Init of $key describes file ${file.id}" }

        if (!admission.fitsLimits(source, file, uploads)) {
            Timber.i("Upload of ${file.id} into source ${source.id} declined: over this device's file limits")
            return UploadTarget.Opening.Answered(FileServerMessages.Upload.OverLimit(key = key))
        }

        val fs = sourceFiles.open(source)

        // Refused before anything is staged, not once it all arrived.
        fs.checkPath(file.path)

        val opened = staging.open(key.toIndexed(), peer.deviceId, file)

        return UploadTarget.Opening.Staged(
            landing = Landing(key, source, file, fs),
            staging = opened.file,
            committed = opened.committed,
        )
    }

    /** Nothing to record for a source: the next pass plans the file again. */
    override suspend fun abandon(peer: PeerIdentity, key: UploadKey, reason: String) {
        key as UploadKey.Source
        authorizer.authorizedSource(peer, key.sourceId)

        Timber.i("Peer ${peer.deviceId} abandoned upload of $key: $reason")
        staging.discard(key.toIndexed(), locator = null)
    }

    private inner class Landing(
        private val key: UploadKey.Source,
        private val source: SourceEntry,
        private val file: FileRecord,
        private val fs: FileSystem,
    ) : UploadLanding {
        override val size: Long get() = file.metadata.size

        override val path: String get() = file.path

        override suspend fun ensureOpen(peer: PeerIdentity) {
            authorizer.authorizedSource(peer, key.sourceId)
        }

        override suspend fun checkpoint(offset: Long) {
            staging.checkpoint(key.toIndexed(), offset)
        }

        /** Places the staged bytes in the source, then indexes them. */
        override suspend fun place(staged: FsFile, hash: ContentHash) {
            // The sender is blocked on this call, with the link idle the whole time it takes, so
            // every part of it is counted.
            val timer = StageTimer("finish ${file.id}")

            // A failed placement keeps staging and its row, so the next attempt only places again.
            val result = timer.time("place") { fs.place(staged, file.path) }

            staging.discard(key.toIndexed(), locator = null)

            // Recorded as the disk reports it, or the next scan reads a mismatch as a local edit.
            val modifiedAt = timer.time("settle-mtime") {
                result.settleLastModified(file.metadata.lastModified)
            }

            timer.time("index-write") {
                indexWriter.recordReceived(
                    source = source,
                    file = file.copy(content = hash),
                    locator = result.locator,
                    modifiedAt = modifiedAt,
                    atRest = result.atRest,
                )
            }

            Timber.i(timer.summary())
        }

        override suspend fun discard(staged: FsFile) {
            staging.discard(key.toIndexed(), staged.locator)
        }
    }
}