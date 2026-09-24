package com.fserver.core.sync.server.handler.upload

import com.fserver.common.exception.TransferException
import com.fserver.common.utils.IdGenerator
import com.fserver.common.utils.StageTimer
import com.fserver.core.files.scan.toFiles
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.RemoteOperation
import com.fserver.core.network.dictionary.dto.toFileRecord
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.toIndexed
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.progress.SyncProgressReporter
import com.fserver.core.sync.server.SessionContext
import com.fserver.core.sync.server.SourceAuthorizer
import com.fserver.core.util.TimeProvider
import com.fserver.files.FilesNode
import com.fserver.net.session.PeerSession
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Receives a file the peer pushes: `Upload.Init`, then chunks, then `Upload.UploadCompleted`.
 *
 * The bytes never touch this class - it opens an [UploadContext] per file and lets that write them
 * off the session collector. See [SessionContext] for why.
 */
internal class FileUploadHandler(
    private val authorizer: SourceAuthorizer,
    private val storage: FServerStorage,
    private val node: FilesNode,
    private val timeProvider: TimeProvider,
    private val progress: SyncProgressReporter,
) {
    suspend fun handle(
        session: PeerSession<FileServerMessages>,
        operation: RemoteOperation.Upload,
        context: SessionContext,
    ) {
        when (operation) {
            is RemoteOperation.Upload.Init -> {
                val source = authorizer.authorizedSource(session.identity, operation.sourceId)
                val now = timeProvider.now()

                // Nothing else clears an upload whose sender stopped mid-stream.
                context.pruneStaleUploads(now)

                val upload = context.start(
                    source = source,
                    file = operation.file.toFileRecord(),
                    fs = node.openSource(source.location.toFiles()),
                    startedAt = now,
                    progress = progress,
                )

                progress.transferStarted(
                    key = upload.transferKey,
                    path = operation.file.path,
                    totalBytes = operation.file.metadata.size
                )

                // TODO: lock file on disk, so no one can edit/delete it

                Timber.i("Upload started for ${operation.file.id} from source ${source.id} by peer ${session.identity.deviceId}")
            }

            is RemoteOperation.Upload.UploadCompleted -> {
                val source = authorizer.authorizedSource(session.identity, operation.key.sourceId)

                val upload = context.uploads.remove(operation.key)
                    ?: throw TransferException.UploadNotFoundException(operation.key.fileId)

                try {
                    finish(operation, source, upload)
                    Timber.i("Upload completed for ${operation.key.fileId} from source ${source.id} by peer ${session.identity.deviceId}")
                } catch (e: Throwable) {
                    // Nothing points at these bytes, and the next attempt starts from zero.
                    // TODO: once rename ran, the locator abandon() deletes may be the finished
                    //  file - a backend that keeps it through a rename (SAF, MediaStore) loses it here
                    withContext(NonCancellable) { upload.abandon() }
                    throw e
                }
            }
        }
    }

    fun queueChunk(
        message: FileServerMessages.UploadChunk,
        context: SessionContext,
    ) {
        val timer = context.collector

        val key = IndexedFileKey(fileId = message.fileId, sourceId = message.sourceId)
        val upload = context.uploads[key]
            ?: throw TransferException.UploadNotFoundException(message.fileId)

        timer.count("bytes", message.bytes.size.toLong())
        timer.count("chunks")
        timer.periodicSummary()?.let(Timber::i)

        // Handed to the upload's own writer rather than written here: this runs on the session
        // collector, and `:net` drops inbound frames while it is not draining (PeerSessionImpl).
        if (timer.time("offer") { upload.offer(message) }) return

        // A refusal is a resend: counting them says the writer, not the link, is the limit.
        timer.count("refused-chunks")

        throw upload.failure ?: TransferException.PendingChunksOverflowException(
            occupiedBytes = context.buffered.get(),
            maxBytes = SessionContext.InFlightChunkBytesLimit,
        )
    }

    /** Waits for the bytes to land, checks them against what the peer promised, then indexes them. */
    private suspend fun finish(
        operation: RemoteOperation.Upload.UploadCompleted,
        source: SourceEntry,
        upload: UploadContext,
    ) {
        // The sender is blocked on this call, with the link idle the whole time it takes, so
        // every part of it is counted.
        val timer = StageTimer("finish ${upload.file.id}")

        // Wait rest of the chunks to arrive
        timer.time("await-chunks") { upload.await() }

        val written = upload.target // Set when chunks are written to disk
            ?: throw TransferException.FileNotFoundException("No bytes written for ${operation.key.fileId} in source ${source.id}")

        val computedHash = timer.time("hash-compute") { upload.hasher.compute() }

        if (operation.hash != computedHash.value || operation.algorithm != computedHash.algorithm) {
            // File was corrupted in transit, or the peer sent the wrong hash.
            // Either way, we cannot trust it.
            throw TransferException.UploadHashMismatchException(
                expectedHash = operation.hash,
                actualHash = computedHash.value,
            )
        }

        val result = if (upload.isDownloadingToTempFile) {
            timer.time("rename") {
                val filename = upload.file.path.substringAfterLast('/')

                written.rename(newName = filename, deleteOldOnConflict = true)
            }
        } else {
            written
        }

        // Recorded as the disk reports it, or the next scan reads a mismatch as a local edit.
        val modifiedAt = timer.time("settle-mtime") {
            result.settleLastModified(upload.file.metadata.lastModified)
        }

        val saved = timer.time("index-lookup") { storage.index.findFile(operation.key) }

        val indexed = upload.file
            .copy(content = computedHash)
            .toIndexed(
                id = saved?.id ?: IdGenerator.nextId,
                sourceId = source.id,
                locator = result.locator,
                currentTime = timeProvider.now(),
            )
            .copy(modifiedAt = modifiedAt)

        timer.time("index-write") { storage.index.markProcessed(listOf(indexed)) }

        progress.transferCompleted(upload.transferKey)

        Timber.i(timer.summary())
    }
}
