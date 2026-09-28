package com.fserver.core.sync.server.handler.upload

import com.fserver.common.exception.TransferException
import com.fserver.common.utils.StageTimer
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.files.scan.toFiles
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.FileServerMessages.Upload
import com.fserver.core.network.dictionary.dto.toFileRecord
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.LocalIndexWriter
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.progress.impl.SyncProgressReporter
import com.fserver.core.sync.server.SessionContext
import com.fserver.core.sync.server.SourceAuthorizer
import com.fserver.core.util.TimeProvider
import com.fserver.files.FilesNode
import com.fserver.net.session.PeerSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Receives a file the peer pushes: [Upload.Init], then chunks, then [Upload.Complete].
 *
 * Chunks land in [UploadStaging], not in the source: the source's backend is touched once, when
 * the file is whole. The bytes never touch this class - it opens an [UploadContext] per file and
 * lets that write them off the session collector. Placing and indexing the file is its last step.
 */
internal class FileUploadHandler(
    private val authorizer: SourceAuthorizer,
    private val admission: UploadAdmission,
    private val indexWriter: LocalIndexWriter,
    private val node: FilesNode,
    private val staging: UploadStaging,
    private val timeProvider: TimeProvider,
    private val progress: SyncProgressReporter,
) {
    /** The upload part of a new [SessionContext], living as long as [scope]. */
    fun sessionUploads(scope: CoroutineScope) = SessionUploads(scope, staging, progress)

    suspend fun handle(
        event: PeerSession.Inbound<FileServerMessages>,
        message: Upload,
        session: PeerSession<FileServerMessages>,
        context: SessionContext,
    ) {
        val answer = runCatchingCancellable { answer(session, message, context.uploads) }
            .getOrElse { t ->
                Timber.w(
                    t,
                    "Upload ${message::class.simpleName} for ${message.key} from ${session.identity.deviceId} failed"
                )
                Upload.Failed(key = message.key, reason = t.message ?: "Unknown error")
            }

        val reply = event.reply
        if (reply == null) {
            Timber.w("Cannot answer ${message::class.simpleName} from ${session.identity.deviceId}: no reply channel")
            return
        }

        reply(answer)
    }

    fun queueChunk(
        message: FileServerMessages.UploadChunk,
        context: SessionContext,
    ) {
        val uploads = context.uploads
        val timer = uploads.collector

        val key = IndexedFileKey(fileId = message.fileId, sourceId = message.sourceId)
        val upload = uploads.inFlight[key]
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
            occupiedBytes = uploads.buffered.get(),
            maxBytes = SessionUploads.InFlightChunkBytesLimit,
        )
    }

    /** The session is gone: its uploads wait in staging for the peer to come back. */
    suspend fun sessionEnded(context: SessionContext) {
        context.uploads.parkAll()
    }

    private suspend fun answer(
        session: PeerSession<FileServerMessages>,
        message: Upload,
        uploads: SessionUploads,
    ): Upload = when (message) {
        is FileServerMessages.Response ->
            throw IllegalStateException("Cannot answer a response: $message")

        is Upload.Init -> init(session, message, uploads)
        is Upload.Status -> status(session, message, uploads)
        is Upload.Complete -> complete(session, message, uploads)
    }

    private suspend fun init(
        session: PeerSession<FileServerMessages>,
        message: Upload.Init,
        uploads: SessionUploads,
    ): Upload {
        val source = authorizer.authorizedSource(session.identity, message.sourceId)

        admission.checkMode(source, session.identity.deviceId, message.key)

        val now = timeProvider.now()
        uploads.pruneStale(now)

        val file = message.file.toFileRecord()

        if (!admission.fitsLimits(source, file, uploads)) {
            Timber.i("Upload of ${file.id} into source ${source.id} declined: over this device's file limits")
            return Upload.OverLimit(key = message.key)
        }

        val fs = node.openSource(source.location.toFiles())

        // Refused before anything is staged, not once it all arrived.
        fs.checkPath(file.path)

        val upload = uploads.start(
            source = source,
            file = file,
            deviceId = session.identity.deviceId,
            fs = fs,
            startedAt = now,
        )

        progress.transferStarted(
            key = upload.transferKey,
            path = file.path,
            totalBytes = file.metadata.size,
        )

        if (upload.prefix > 0) progress.transferAdvanced(upload.transferKey, upload.prefix)

        Timber.i("Upload of ${file.id} into source ${source.id} by peer ${session.identity.deviceId} starts at ${upload.prefix}")

        return Upload.Received(key = upload.key, offset = upload.prefix)
    }

    /** A checkpoint: the answer is what survives a crash from here on. */
    private suspend fun status(
        session: PeerSession<FileServerMessages>,
        message: Upload.Status,
        uploads: SessionUploads,
    ): Upload {
        authorizer.authorizedSource(session.identity, message.key.sourceId)

        val upload = uploads.inFlight[message.key]
            ?: throw TransferException.UploadNotFoundException(message.key.fileId)

        // A dead writer takes no more chunks: the sender Inits again, which parks this one.
        upload.failure?.let { throw it }

        val offset = upload.flush()
        staging.checkpoint(upload.key, offset)

        return Upload.Received(key = upload.key, offset = offset)
    }

    private suspend fun complete(
        session: PeerSession<FileServerMessages>,
        message: Upload.Complete,
        uploads: SessionUploads,
    ): Upload {
        val source = authorizer.authorizedSource(session.identity, message.key.sourceId)

        val upload = uploads.inFlight.remove(message.key)
            ?: throw TransferException.UploadNotFoundException(message.key.fileId)

        val written = runCatchingCancellable { upload.await() }

        // Refused chunks, or a writer that died: park what arrived, the sender Inits again.
        if (written.isFailure || !upload.isWhole) {
            withContext(NonCancellable) { uploads.park(upload) }
            written.exceptionOrNull()?.let { throw it }

            return Upload.Received(key = upload.key, offset = upload.prefix)
        }

        try {
            finish(message, source, upload)
        } catch (e: Throwable) {
            progress.transferFailed(upload.transferKey, e)
            throw e
        }

        Timber.i("Upload completed for ${message.key.fileId} from source ${source.id} by peer ${session.identity.deviceId}")

        return Upload.Completed(upload.key)
    }

    /** Checks the staged bytes against the hash, places them in the source, then indexes them. */
    private suspend fun finish(
        message: Upload.Complete,
        source: SourceEntry,
        upload: UploadContext,
    ) {
        // The sender is blocked on this call, with the link idle the whole time it takes, so
        // every part of it is counted.
        val timer = StageTimer("finish ${upload.file.id}")

        // Whole and flushed: a placement that fails is retried without a byte resent.
        timer.time("flush") { staging.checkpoint(upload.key, upload.flush()) }
        upload.stop()

        val computedHash = upload.hash()

        if (message.hash != computedHash.value || message.algorithm != computedHash.algorithm) {
            // File was corrupted in transit, or the peer sent the wrong hash.
            // Either way, we cannot trust it, and nothing staged is worth resuming.
            withContext(NonCancellable) { staging.discard(upload.key, upload.staging.locator) }

            throw TransferException.UploadHashMismatchException(
                expectedHash = message.hash,
                actualHash = computedHash.value,
            )
        }

        // A failed placement keeps staging and its row, so the next attempt only places again.
        val result = timer.time("place") { upload.fs.place(upload.staging, upload.file.path) }

        staging.discard(upload.key, locator = null)

        // Recorded as the disk reports it, or the next scan reads a mismatch as a local edit.
        val modifiedAt = timer.time("settle-mtime") {
            result.settleLastModified(upload.file.metadata.lastModified)
        }

        timer.time("index-write") {
            indexWriter.recordReceived(
                source = source,
                file = upload.file.copy(content = computedHash),
                locator = result.locator,
                modifiedAt = modifiedAt,
            )
        }

        progress.transferCompleted(upload.transferKey)

        Timber.i(timer.summary())
    }
}
