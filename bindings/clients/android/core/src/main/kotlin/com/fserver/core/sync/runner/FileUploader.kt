package com.fserver.core.sync.runner

import com.fserver.core.files.scan.toFiles
import com.fserver.core.files.util.FileHasher
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.RemoteOperation
import com.fserver.core.network.dictionary.codec.UploadChunkCodec
import com.fserver.core.network.dictionary.dto.toDto
import com.fserver.core.network.utils.runRemoteOperation
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.SourceEntry
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.files.FilesNode
import com.fserver.files.upload.FileRecord
import com.fserver.net.session.PeerSession
import java.io.InputStream

/**
 * Streams one local file to the peer: [RemoteOperation.Upload.Init], chunks, then
 * [RemoteOperation.Upload.UploadCompleted] with the hash. Split out of [FileActionRunner] because it
 * is the one action with a multi-message protocol of its own.
 */
internal class FileUploader(
    private val storage: FServerStorage,
    private val node: FilesNode,
) {
    suspend fun uploadFile(
        file: FileRecord,
        source: SourceEntry,
        session: PeerSession<FileServerMessages>,
    ) {
        val locator = file.locator
            ?: error("Cannot upload file ${file.id} because it has no locator")

        // Run as operation to confirm that the peer is ready to receive the file
        session.runRemoteOperation(
            operation = RemoteOperation.Upload.Init(
                sourceId = source.id,
                file = file.toDto(),
            )
        )

        val chunkSize = chunkSize(session, source.id, file.id.value)
        val hasher = if (file.content == null) FileHasher() else null

        val fs = node.openSource(source.location.toFiles())
        fs.openFile(locator).use { stream ->
            var offset = 0L
            val buffer = ByteArray(chunkSize)

            while (true) {
                val bytesRead = stream.fill(buffer)
                if (bytesRead == 0) break

                hasher?.write(buffer, bytesRead)

                val chunk = buffer.copyOf(bytesRead)

                // TODO: ask peer about it's state each N chunks, to retry/resume/abort if needed
                session.send(
                    FileServerMessages.UploadChunk(
                        sourceId = source.id,
                        fileId = file.id.value,
                        offset = offset,
                        bytes = chunk,
                    )
                ).getOrThrow()

                offset += bytesRead
            }
        }

        val hash = hasher?.compute() ?: file.content!!

        session.runRemoteOperation(
            operation = RemoteOperation.Upload.UploadCompleted(
                key = IndexedFileKey(fileId = file.id.value, sourceId = source.id),
                hash = hash.value,
                algorithm = hash.algorithm,
            )
        )

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
