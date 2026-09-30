package com.fserver.core.sync.transfer

import com.fserver.common.exception.SyncException
import com.fserver.common.exception.TransferException
import com.fserver.common.model.ContentHash
import com.fserver.common.utils.StageTimer
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.FileServerMessages.Upload
import com.fserver.core.network.dictionary.codec.UploadChunkCodec
import com.fserver.core.network.dictionary.dto.UploadKey
import com.fserver.core.sync.progress.FileTransfer.Direction.Outgoing
import com.fserver.core.sync.progress.impl.SyncProgressReporter
import com.fserver.core.sync.transfer.FilePusher.Companion.MinChunkSize
import com.fserver.files.fs.FsFile
import com.fserver.net.session.PeerSession
import timber.log.Timber

/**
 * Streams one local file to the peer under an [UploadKey]: [Upload.Init], chunks, then
 * [Upload.Complete] with the hash. Shared by [SourceUploader] and one-shot transfers.
 *
 * The receiver stages what arrives and answers every step with how far it got, so a dropped
 * upload resumes from there instead of starting over - within this call, and across calls.
 */
internal class FilePusher(
    private val progress: SyncProgressReporter,
) {
    /**
     * Streams [file] under [init]'s key: [Upload.Init], chunks, then [Upload.Complete] with the hash.
     * Reported as one transfer whichever way it was asked for: a pass pushing the file, a peer asking
     * us to hand it back, or a one-shot transfer. All are this device sending bytes.
     *
     * @param knownHash the file's hash when already known; computed on the way otherwise.
     * @return hash of the bytes sent, or null when the receiver already held the whole file.
     * @throws TransferException.UploadStoppedException when the receiver takes nothing more under
     *   the key's owner - a one-shot transfer cancelled there.
     */
    suspend fun push(
        session: PeerSession<FileServerMessages>,
        init: Upload.Init,
        file: FsFile,
        path: String,
        size: Long,
        knownHash: ContentHash?,
    ): ContentHash? {
        val key = init.key

        return try {
            stream(
                session = session,
                init = init,
                file = file,
                path = path,
                size = size,
                knownHash = knownHash
            ).also {
                if (it != null) progress.uploadCompleted(Outgoing, key) else progress.uploadSkipped(
                    Outgoing,
                    key
                )
            }
        } catch (e: SyncException.OverLimitException) {
            progress.uploadSkipped(Outgoing, key)
            throw e
        } catch (e: Throwable) {
            progress.uploadFailed(Outgoing, key, e)
            throw e
        }
    }

    /** Tells the receiver this device gives the file up - it cannot read it anymore. */
    suspend fun abandon(session: PeerSession<FileServerMessages>, key: UploadKey, reason: String) {
        session.ask(Upload.Abandon(key, reason))
        progress.uploadSkipped(Outgoing, key)
    }

    private suspend fun stream(
        session: PeerSession<FileServerMessages>,
        init: Upload.Init,
        file: FsFile,
        path: String,
        size: Long,
        knownHash: ContentHash?,
    ): ContentHash? {
        val key = init.key

        // Instrumentation: a slow upload is disk, hashing, crypto or the socket, and the only way
        // to tell is to count. See StageTimer.enabled to take it back out.
        val timer = StageTimer("upload $key")

        val chunkSize = chunkSize(session, key)
        timer.count("chunkSize", chunkSize.toLong())

        val digest = if (knownHash == null) ProgressiveHash() else null

        var resumeFrom = timer.time("init-rtt") { session.ask(init).offset() } ?: return null
        progress.uploadStarted(Outgoing, key, path, size)

        repeat(MaxAttempts) { attempt ->
            if (attempt > 0) {
                timer.count("resumes")
                Timber.i("Resuming upload of $key from $resumeFrom, attempt ${attempt + 1}")
            }

            val sent = sendFrom(
                offset = resumeFrom,
                file = file,
                key = key,
                chunkSize = chunkSize,
                digest = digest,
                session = session,
                timer = timer,
            )

            if (sent) {
                val hash = digest?.hash ?: knownHash!!

                val answer = timer.time("completed-rtt") {
                    session.ask(
                        Upload.Complete(
                            key = key,
                            hash = hash.value,
                            algorithm = hash.algorithm
                        )
                    )
                }

                if (answer is Upload.Completed) {
                    Timber.i(timer.summary())
                    return hash
                }
            }

            // Lost, or bytes went missing on the way: the receiver parked what it has, so open it again.
            resumeFrom = timer.time("init-rtt") { session.ask(init).offset() } ?: return null
        }

        throw SyncException.RemoteRejectedException(
            "Peer ${session.identity.deviceId} did not take $key in $MaxAttempts attempts"
        )
    }

    /**
     * Sends [file] from [offset] to its end.
     *
     * @return false when the receiver no longer knows the upload, so it must be [Upload.Init]ed again.
     */
    private suspend fun sendFrom(
        offset: Long,
        file: FsFile,
        key: UploadKey,
        chunkSize: Int,
        digest: ProgressiveHash?,
        session: PeerSession<FileServerMessages>,
        timer: StageTimer,
    ): Boolean = file.read().use { stream ->
        val buffer = ByteArray(chunkSize)
        val statusEvery = (StatusIntervalBytes / chunkSize).toInt().coerceAtLeast(1)

        // Bytes the receiver already has are read only if the hash has not seen them yet.
        var position =
            timer.time("skip") { stream.skipFully(minOf(offset, digest?.hashedTo ?: offset)) }

        // refill digest with bytes the receiver already has
        while (position < offset) {
            val read = timer.time("disk-read") {
                stream.fill(buffer, length = minOf(buffer.size.toLong(), offset - position).toInt())
            }
            if (read == 0) break

            timer.time("hash") { digest?.feed(position, buffer, read) }
            position += read
        }

        var sinceStatus = 0

        // send bytes the receiver does not have yet, feeding the hash as we go
        while (true) {
            val bytesRead = timer.time("disk-read") { stream.fill(buffer) }
            if (bytesRead == 0) break

            timer.time("hash") { digest?.feed(position, buffer, bytesRead) }

            val chunk = timer.time("copy") { buffer.copyOf(bytesRead) }

            timer.time("send") {
                session.send(
                    FileServerMessages.UploadChunk(
                        key = key,
                        offset = position,
                        // Trimmed to what was read: the receiver takes the length from the frame.
                        bytes = chunk,
                    )
                ).getOrThrow()
            }

            position += bytesRead
            progress.uploadAdvanced(Outgoing, key, position)

            timer.count("bytes", bytesRead.toLong())
            timer.count("chunks")

            if (++sinceStatus == statusEvery) {
                sinceStatus = 0
                if (!timer.time("status-rtt") { session.status(key) }) return@use false
            }
        }

        true
    }

    /** True while the receiver still has the upload open. Its answer is its checkpoint. */
    private suspend fun PeerSession<FileServerMessages>.status(uploadKey: UploadKey): Boolean =
        when (val answer = request(Upload.Status(uploadKey)).getOrThrow()) {
            is Upload.Received -> true

            is Upload.Stopped -> throw TransferException.UploadStoppedException(answer.reason)

            is Upload.Failed -> {
                Timber.i("Peer ${identity.deviceId} lost upload $uploadKey: ${answer.reason}")
                false
            }

            else -> error("Unexpected response to Upload.Status: $answer")
        }

    /** Asks [message] and returns the answer, throwing when the peer refused it. */
    private suspend fun PeerSession<FileServerMessages>.ask(message: Upload): Upload =
        when (val answer = request(message).getOrThrow()) {
            is Upload.Failed -> throw SyncException.RemoteRejectedException(
                "Peer ${identity.deviceId} refused ${message::class.simpleName} for ${message.key}: ${answer.reason}"
            )

            is Upload.Stopped -> throw TransferException.UploadStoppedException(answer.reason)

            is Upload.OverLimit -> throw SyncException.OverLimitException(
                "Peer ${identity.deviceId} has no room for ${message.key} under its file limits"
            )

            is Upload -> answer.also {
                check(it.key == message.key) { "Answer for ${it.key} to ${message.key}" }
            }

            else -> error("Unexpected response to ${message::class.simpleName}: $answer")
        }

    companion object {
        /** Only reachable on a link whose frames barely fit a handshake; the session then splits. */
        const val MinChunkSize = 4 * 1024 // 4 KiB

        /** How often the receiver is asked to checkpoint: what a crash costs, at most. */
        private const val StatusIntervalBytes = 64L * 1024 * 1024 // 64 MiB

        /** Inits, one per lost upload or missing bytes, before the upload is given up. */
        private const val MaxAttempts = 3
    }
}

/** Where to send from, or null when the receiver already holds the whole file. */
private fun Upload.offset(): Long? = when (this) {
    is Upload.Received -> offset
    is Upload.Completed -> null
    else -> error("Expected Upload.Received, got $this")
}

/**
 * How many bytes of file go in one message, so that the message fills one frame and no more.
 *
 * The session carries a bigger message by splitting it, which costs a second frame for a
 * handful of bytes; sizing the chunk to what a frame actually holds - its payload budget, less
 * what the codec writes around the bytes - avoids the split rather than relying on it.
 */
private fun chunkSize(
    session: PeerSession<FileServerMessages>,
    key: UploadKey,
): Int = (session.maxPayloadSize - UploadChunkCodec.headerSize(key))
    .coerceAtLeast(MinChunkSize)
