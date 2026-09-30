package com.fserver.core.sync.server.handler.upload

import com.fserver.common.exception.TransferException
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.FileServerMessages.Upload
import com.fserver.core.network.dictionary.dto.UploadKey
import com.fserver.core.sync.progress.FileTransfer
import com.fserver.core.sync.progress.impl.SyncProgressReporter
import com.fserver.core.sync.server.SessionContext
import com.fserver.core.sync.server.handler.upload.oneshot.OneShotUploadTarget
import com.fserver.core.sync.server.handler.upload.source.SourceUploadTarget
import com.fserver.core.util.TimeProvider
import com.fserver.net.session.PeerSession
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Receives a file the peer pushes: [Upload.Init], then chunks, then [Upload.Complete].
 *
 * Chunks land in staging, not in the destination: the destination is touched once, when the file
 * is whole. The bytes never touch this class - it opens an [UploadContext] per file and lets that
 * write them off the session collector.
 *
 * The same for every [UploadKey]. Who may send a file and where it lands is the [UploadTarget] of
 * its kind: [com.fserver.core.sync.server.handler.upload.source.SourceUploadTarget] for a source's file, [com.fserver.core.sync.server.handler.upload.oneshot.OneShotUploadTarget] for a one-shot's.
 */
internal class FileUploadHandler(
    private val sources: SourceUploadTarget,
    private val oneShots: OneShotUploadTarget,
    private val timeProvider: TimeProvider,
    private val progress: SyncProgressReporter,
) {
    /** The upload part of a new [SessionContext], living as long as [scope]. */
    fun sessionUploads(scope: CoroutineScope) = SessionUploads(scope, progress)

    suspend fun handle(
        event: PeerSession.Inbound<FileServerMessages>,
        message: Upload,
        session: PeerSession<FileServerMessages>,
        context: SessionContext,
    ) {
        val answer = runCatchingCancellable { answer(session, message, context.uploads) }
            .getOrElse { t ->
                if (t is TransferException.UploadStoppedException) {
                    return@getOrElse Upload.Stopped(key = message.key, reason = t.message.orEmpty())
                }

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

        val upload = uploads.inFlight[message.key]
            ?: throw TransferException.UploadNotFoundException(message.key.toString())

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

    private fun targetOf(key: UploadKey): UploadTarget = when (key) {
        is UploadKey.Source -> sources
        is UploadKey.OneShot -> oneShots
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
        is Upload.Abandon -> abandon(session, message, uploads)
    }

    private suspend fun init(
        session: PeerSession<FileServerMessages>,
        message: Upload.Init,
        uploads: SessionUploads,
    ): Upload {
        val now = timeProvider.now()
        uploads.pruneStale(now)
        uploads.reserve(message.key)

        val target = targetOf(message.key)
        val opening = target.open(peer = session.identity, init = message, uploads = uploads)
        val staged = when (opening) {
            is UploadTarget.Opening.Answered -> return opening.answer
            is UploadTarget.Opening.Staged -> opening
        }

        val upload = uploads.start(message.key, staged, now)

        progress.uploadStarted(
            direction = FileTransfer.Direction.Incoming,
            key = upload.key,
            path = upload.landing.path,
            totalBytes = upload.landing.size
        )
        if (upload.prefix > 0) progress.uploadAdvanced(
            direction = FileTransfer.Direction.Incoming,
            key = upload.key,
            transferredBytes = upload.prefix
        )

        Timber.i("Upload of ${upload.key} by peer ${session.identity.deviceId} starts at ${upload.prefix}")

        return Upload.Received(key = upload.key, offset = upload.prefix)
    }

    /** A checkpoint: the answer is what survives a crash from here on. */
    private suspend fun status(
        session: PeerSession<FileServerMessages>,
        message: Upload.Status,
        uploads: SessionUploads,
    ): Upload {
        val upload = uploads.inFlight[message.key]
            ?: throw TransferException.UploadNotFoundException(message.key.toString())

        ensureOpen(session, upload, uploads)

        // A dead writer takes no more chunks: the sender Inits again, which parks this one.
        upload.failure?.let { throw it }

        val offset = upload.flush()
        upload.landing.checkpoint(offset)

        return Upload.Received(key = upload.key, offset = offset)
    }

    private suspend fun complete(
        session: PeerSession<FileServerMessages>,
        message: Upload.Complete,
        uploads: SessionUploads,
    ): Upload {
        val upload = uploads.inFlight[message.key]
            ?: throw TransferException.UploadNotFoundException(message.key.toString())

        ensureOpen(session, upload, uploads)
        uploads.inFlight.remove(message.key, upload)

        val written = runCatchingCancellable { upload.await() }

        // Refused chunks, or a writer that died: park what arrived, the sender Inits again.
        if (written.isFailure || !upload.isWhole) {
            withContext(NonCancellable) { uploads.park(upload) }
            written.exceptionOrNull()?.let { throw it }

            return Upload.Received(key = upload.key, offset = upload.prefix)
        }

        try {
            finish(message, upload)
        } catch (e: Throwable) {
            progress.uploadFailed(FileTransfer.Direction.Incoming, upload.key, e)
            throw e
        }

        Timber.i("Upload completed for ${message.key} by peer ${session.identity.deviceId}")

        return Upload.Completed(upload.key)
    }

    /** Checks the staged bytes against the hash, then hands them to the landing. */
    private suspend fun finish(message: Upload.Complete, upload: UploadContext) {
        // Whole and flushed: a placement that fails is retried without a byte resent.
        upload.landing.checkpoint(upload.flush())
        upload.stop()

        val computedHash = upload.hash()

        if (message.hash != computedHash.value || message.algorithm != computedHash.algorithm) {
            // File was corrupted in transit, or the peer sent the wrong hash.
            // Either way, we cannot trust it, and nothing staged is worth resuming.
            withContext(NonCancellable) { upload.landing.discard(upload.staging) }

            throw TransferException.UploadHashMismatchException(
                expectedHash = message.hash,
                actualHash = computedHash.value,
            )
        }

        upload.landing.place(upload.staging, computedHash)

        progress.uploadCompleted(FileTransfer.Direction.Incoming, upload.key)
    }

    private suspend fun abandon(
        session: PeerSession<FileServerMessages>,
        message: Upload.Abandon,
        uploads: SessionUploads,
    ): Upload {
        uploads.inFlight.remove(message.key)?.let { uploads.park(it) }
        targetOf(message.key).abandon(session.identity, message.key, message.reason)

        return Upload.Completed(message.key)
    }

    /** Throws when [session]'s peer may not go on with [upload]; parks it when nobody may. */
    private suspend fun ensureOpen(
        session: PeerSession<FileServerMessages>,
        upload: UploadContext,
        uploads: SessionUploads,
    ) {
        try {
            upload.landing.ensureOpen(session.identity)
        } catch (e: TransferException.UploadStoppedException) {
            if (uploads.inFlight.remove(upload.key, upload)) uploads.park(upload)
            throw e
        }
    }
}
