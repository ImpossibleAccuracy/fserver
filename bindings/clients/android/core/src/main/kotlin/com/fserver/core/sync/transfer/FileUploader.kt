package com.fserver.core.sync.transfer

import com.fserver.common.exception.SyncException
import com.fserver.common.exception.TransferException
import com.fserver.common.model.ContentHash
import com.fserver.common.utils.StageTimer
import com.fserver.core.files.scan.toFiles
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.FileServerMessages.Upload
import com.fserver.core.network.dictionary.codec.UploadChunkCodec
import com.fserver.core.network.dictionary.dto.ContentHashDto
import com.fserver.core.network.dictionary.dto.toDto
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.LocalIndexWriter
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.progress.FileTransferKey
import com.fserver.core.sync.progress.impl.SyncProgressReporter
import com.fserver.core.sync.remote.PeerIndexFetcher
import com.fserver.core.sync.transfer.FileUploader.Companion.MinChunkSize
import com.fserver.files.FilesNode
import com.fserver.files.fs.FsFile
import com.fserver.files.upload.FileRecord
import com.fserver.files.upload.FileVersion
import com.fserver.net.session.PeerSession
import timber.log.Timber

/**
 * Streams one local file to the peer: [Upload.Init], chunks, then [Upload.Complete] with the hash.
 * Used by a pass pushing a file, and by the server handing one back to the peer that asked.
 *
 * The receiver stages what arrives and answers every step with how far it got, so a dropped
 * upload resumes from there instead of starting over - within this call, and across passes.
 */
internal class FileUploader(
    private val indexWriter: LocalIndexWriter,
    private val remoteIndex: PeerIndexFetcher,
    private val node: FilesNode,
    private val progress: SyncProgressReporter,
) {
    /**
     * Reported as one transfer whichever way it was asked for: a pass pushing the file, or a peer
     * asking us to hand it back. Both are this device sending bytes.
     *
     * @return hash of the bytes sent
     */
    suspend fun uploadFile(
        file: FileRecord,
        version: FileVersion? = file.metadata.version,
        source: SourceEntry,
        session: PeerSession<FileServerMessages>,
    ): ContentHash {
        val key = FileTransferKey.outgoing(source.id, file.id.value)

        return try {
            stream(
                file = file,
                version = version,
                source = source,
                session = session,
                key = key,
            ).also { progress.transferCompleted(key) }
        } catch (e: SyncException.OverLimitException) {
            progress.transferSkipped(key)
            throw e
        } catch (e: Throwable) {
            progress.transferFailed(key, e)
            throw e
        }
    }

    private suspend fun stream(
        file: FileRecord,
        version: FileVersion?,
        source: SourceEntry,
        session: PeerSession<FileServerMessages>,
        key: FileTransferKey,
    ): ContentHash {
        val locator = file.locator
            ?: error("Cannot upload file ${file.id} because it has no locator")

        // Instrumentation: a slow upload is disk, hashing, crypto or the socket, and the only way
        // to tell is to count. See StageTimer.enabled to take it back out.
        val timer = StageTimer("upload ${file.id}")

        val uploadKey = IndexedFileKey(fileId = file.id.value, sourceId = source.id)
        val init = Upload.Init(
            sourceId = source.id,
            file = file.toDto(
                sourceId = source.id,
                version = version,
            ),
        )

        val chunkSize = chunkSize(session, source.id, file.id.value)
        timer.count("chunkSize", chunkSize.toLong())

        val fs = node.openSource(source.location.toFiles())
        val opened = fs.openFile(locator)
            ?: throw TransferException.FileNotFoundException("File ${file.id} is gone from $locator")

        val digest = if (file.content == null) ProgressiveHash() else null

        var resumeFrom = timer.time("init-rtt") { session.ask(init).offset() }
        progress.transferStarted(key, file.path, file.metadata.size)

        repeat(MaxAttempts) { attempt ->
            if (attempt > 0) {
                timer.count("resumes")
                Timber.i("Resuming upload of ${file.id} from $resumeFrom, attempt ${attempt + 1}")
            }

            val sent = sendFrom(
                offset = resumeFrom,
                file = opened,
                uploadKey = uploadKey,
                chunkSize = chunkSize,
                digest = digest,
                session = session,
                key = key,
                timer = timer,
            )

            if (!sent) {
                resumeFrom = timer.time("init-rtt") { session.ask(init).offset() }
                return@repeat
            }

            val hash = digest?.hash ?: file.content!!

            val answer = timer.time("completed-rtt") {
                session.ask(
                    Upload.Complete(
                        key = uploadKey,
                        hash = hash.value,
                        algorithm = hash.algorithm,
                    )
                )
            }

            if (answer is Upload.Completed) {
                Timber.i(timer.summary())

                if (digest != null) {
                    indexWriter.recordHash(source, file, hash)
                }

                remoteIndex.recordSent(
                    source = source,
                    file = init.file.copy(content = ContentHashDto(value = hash.value, algorithm = hash.algorithm)),
                )

                return hash
            }

            // Bytes went missing on the way: the receiver parked what it has, so open it again.
            resumeFrom = timer.time("init-rtt") { session.ask(init).offset() }
        }

        throw SyncException.RemoteRejectedException(
            "Peer ${session.identity.deviceId} did not take ${file.id} in $MaxAttempts attempts"
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
        uploadKey: IndexedFileKey,
        chunkSize: Int,
        digest: ProgressiveHash?,
        session: PeerSession<FileServerMessages>,
        key: FileTransferKey,
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
                        sourceId = uploadKey.sourceId,
                        fileId = uploadKey.fileId,
                        offset = position,
                        // Trimmed to what was read: the receiver takes the length from the frame.
                        bytes = chunk,
                    )
                ).getOrThrow()
            }

            position += bytesRead
            progress.transferAdvanced(key, position)

            timer.count("bytes", bytesRead.toLong())
            timer.count("chunks")

            if (++sinceStatus == statusEvery) {
                sinceStatus = 0
                if (!timer.time("status-rtt") { session.status(uploadKey) }) return@use false
            }
        }

        true
    }

    /** True while the receiver still has the upload open. Its answer is its checkpoint. */
    private suspend fun PeerSession<FileServerMessages>.status(uploadKey: IndexedFileKey): Boolean =
        when (val answer = request(Upload.Status(uploadKey)).getOrThrow()) {
            is Upload.Received -> true

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

private fun Upload.offset(): Long =
    (this as? Upload.Received)?.offset ?: error("Expected Upload.Received, got $this")

/**
 * How many bytes of file go in one message, so that the message fills one frame and no more.
 *
 * The session carries a bigger message by splitting it, which costs a second frame for a
 * handful of bytes; sizing the chunk to what a frame actually holds - its payload budget, less
 * what the codec writes around the bytes - avoids the split rather than relying on it.
 */
private fun chunkSize(
    session: PeerSession<FileServerMessages>,
    sourceId: String,
    fileId: String,
): Int = (session.maxPayloadSize - UploadChunkCodec.headerSize(sourceId, fileId))
    .coerceAtLeast(MinChunkSize)
