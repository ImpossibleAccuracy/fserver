package com.fserver.core.sync.server.handler.upload

import com.fserver.common.exception.TransferException
import com.fserver.common.utils.IdGenerator
import com.fserver.core.files.scan.toFiles
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.RemoteOperation
import com.fserver.core.network.dictionary.dto.toFileRecord
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.SourceEntry
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.toIndexed
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
 * off the session collector. See [com.fserver.core.sync.server.SessionContext] for why.
 */
internal class FileUploadHandler(
    private val authorizer: SourceAuthorizer,
    private val storage: FServerStorage,
    private val node: FilesNode,
    private val timeProvider: TimeProvider,
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

                // TODO: delete file if it exists already

                // Nothing else clears an upload whose sender stopped mid-stream.
                context.pruneStaleUploads(now)

                context.start(
                    key = IndexedFileKey(fileId = operation.file.id, sourceId = source.id),
                    file = operation.file.toFileRecord(),
                    fs = node.openSource(source.location.toFiles()),
                    startedAt = now,
                )

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
                    withContext(NonCancellable) { upload.abandon() }
                    throw e
                }
            }
        }
    }

    suspend fun queueChunk(
        session: PeerSession<FileServerMessages>,
        message: FileServerMessages.UploadChunk,
        context: SessionContext,
    ) {
        val source = authorizer.authorizedSource(session.identity, message.sourceId)
        val key = IndexedFileKey(fileId = message.fileId, sourceId = source.id)
        val upload = context.uploads[key]
            ?: throw TransferException.UploadNotFoundException(message.fileId)

        // Handed to the upload's own writer rather than written here: this runs on the session
        // collector, and `:net` drops inbound frames while it is not draining (PeerSessionImpl).
        if (upload.offer(message)) return

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
        // Wait rest of the chunks to arrive
        upload.await()

        val locator = upload.locator // Locator is set when chunks are written to disk
            ?: throw TransferException.FileNotFoundException("No bytes written for ${operation.key.fileId} in source ${source.id}")

        val computedHash = upload.hasher.compute()

        if (operation.hash != computedHash.value || operation.algorithm != computedHash.algorithm) {
            // File was corrupted in transit, or the peer sent the wrong hash.
            // Either way, we cannot trust it.
            throw TransferException.UploadHashMismatchException(
                expectedHash = operation.hash,
                actualHash = computedHash.value,
            )
        }

        val saved = storage.index.findFile(operation.key)

        val indexed = upload.file
            .copy(content = computedHash)
            .toIndexed(
                id = saved?.id ?: IdGenerator.nextId,
                sourceId = source.id,
                locator = locator,
                currentTime = timeProvider.now(),
            )

        storage.index.markProcessed(listOf(indexed))
    }
}
