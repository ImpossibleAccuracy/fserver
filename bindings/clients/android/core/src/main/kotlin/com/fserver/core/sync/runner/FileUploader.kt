package com.fserver.core.sync.runner

import com.fserver.common.utils.StageTimer
import com.fserver.core.files.scan.toFiles
import com.fserver.core.files.util.FileHasher
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.RemoteOperation
import com.fserver.core.network.dictionary.codec.UploadChunkCodec
import com.fserver.core.network.dictionary.dto.toDto
import com.fserver.core.network.utils.runRemoteOperation
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.progress.FileTransferKey
import com.fserver.core.sync.progress.SyncProgressReporter
import com.fserver.files.FilesNode
import com.fserver.files.upload.FileRecord
import com.fserver.net.session.PeerSession
import timber.log.Timber
import java.io.InputStream

/**
 * Streams one local file to the peer: [RemoteOperation.Upload.Init], chunks, then
 * [RemoteOperation.Upload.UploadCompleted] with the hash. Split out of [FileActionRunner] because it
 * is the one action with a multi-message protocol of its own.
 */
internal class FileUploader(
    private val storage: FServerStorage,
    private val node: FilesNode,
    private val progress: SyncProgressReporter,
) {
    /**
     * Reported as one transfer whichever way it was asked for: a pass pushing the file, or a peer
     * asking us to hand it back. Both are this device sending bytes.
     */
    suspend fun uploadFile(
        file: FileRecord,
        source: SourceEntry,
        session: PeerSession<FileServerMessages>,
    ) {
        val key = SyncProgressReporter.outgoing(source, file.id.value)

        try {
            stream(file, source, session, key)
            progress.transferCompleted(key)
        } catch (e: Throwable) {
            progress.transferFailed(key, e)
            throw e
        }
    }

    private suspend fun stream(
        file: FileRecord,
        source: SourceEntry,
        session: PeerSession<FileServerMessages>,
        key: FileTransferKey,
    ) {
        val locator = file.locator
            ?: error("Cannot upload file ${file.id} because it has no locator")

        // Instrumentation: a slow upload is disk, hashing, crypto or the socket, and the only way
        // to tell is to count. See StageTimer.enabled to take it back out.
        val timer = StageTimer("upload ${file.id}")

        // Run as operation to confirm that the peer is ready to receive the file
        timer.time("init-rtt") {
            session.runRemoteOperation(
                operation = RemoteOperation.Upload.Init(
                    sourceId = source.id,
                    file = file.toDto(source.id),
                )
            )
        }

        progress.transferStarted(key, file.path, file.metadata.size)

        val chunkSize = chunkSize(session, source.id, file.id.value)
        val hasher = if (file.content == null) FileHasher() else null

        timer.count("chunkSize", chunkSize.toLong())

        val fs = node.openSource(source.location.toFiles())
        fs.openFile(locator).use { stream ->
            var offset = 0L
            val buffer = ByteArray(chunkSize)

            while (true) {
                val bytesRead = timer.time("disk-read") { stream.fill(buffer) }
                if (bytesRead == 0) break

                timer.time("hash") { hasher?.write(buffer, bytesRead) }

                val chunk = timer.time("copy") { buffer.copyOf(bytesRead) }

                // TODO: ask peer about it's state each N chunks, to retry/resume/abort if needed
                timer.time("send") {
                    session.send(
                        FileServerMessages.UploadChunk(
                            sourceId = source.id,
                            fileId = file.id.value,
                            offset = offset,
                            // Trimmed to what was read: the receiver takes the length from the frame.
                            bytes = chunk,
                        )
                    ).getOrThrow()
                }

                offset += bytesRead
                progress.transferAdvanced(key, offset)

                timer.count("bytes", bytesRead.toLong())
                timer.count("chunks")
            }
        }

        val hash = hasher?.compute() ?: file.content!!

        timer.time("completed-rtt") {
            session.runRemoteOperation(
                operation = RemoteOperation.Upload.UploadCompleted(
                    key = IndexedFileKey(fileId = file.id.value, sourceId = source.id),
                    hash = hash.value,
                    algorithm = hash.algorithm,
                )
            )
        }

        Timber.i(timer.summary())

        if (hasher != null) {
            storage.index.saveHash(
                key = IndexedFileKey(fileId = file.id.value, sourceId = source.id),
                hash = hash,
            )
        }
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
        sourceId: String,
        fileId: String,
    ): Int = (session.maxPayloadSize - UploadChunkCodec.headerSize(sourceId, fileId))
        .coerceAtLeast(MinChunkSize)

    /**
     * Fills [buffer] to the brim, or to the end of the file.
     *
     * A single read is free to return less than it was asked for, and every short read would be a
     * frame carrying less than it could - the whole point of sizing the buffer to the frame.
     */
    private fun InputStream.fill(buffer: ByteArray): Int {
        var filled = 0

        while (filled < buffer.size) {
            val read = read(buffer, filled, buffer.size - filled)
            if (read == -1) break
            filled += read
        }

        return filled
    }

    companion object {
        /** Only reachable on a link whose frames barely fit a handshake; the session then splits. */
        private const val MinChunkSize = 4 * 1024 // 4 KiB
    }
}
