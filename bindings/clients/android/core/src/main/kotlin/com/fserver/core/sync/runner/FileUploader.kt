package com.fserver.core.sync.runner

import com.fserver.core.files.scan.toFiles
import com.fserver.core.files.util.FileHasher
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.RemoteOperation
import com.fserver.core.network.dictionary.dto.toDto
import com.fserver.core.network.utils.runRemoteOperation
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.SourceEntry
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.files.FilesNode
import com.fserver.files.upload.FileRecord
import com.fserver.net.session.PeerSession

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

        val hasher = if (file.content == null) FileHasher() else null

        val fs = node.openSource(source.location.toFiles())
        fs.openFile(locator).use { steam ->
            var offset = 0L
            val buffer = ByteArray(CHUNK_SIZE)

            while (true) {
                val bytesRead = steam.read(buffer)
                if (bytesRead == -1) break

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
                key = IndexedFileKey(file.id.value, source.id),
                hash = hash.value,
                algorithm = hash.algorithm,
            )
        )

        if (hasher != null) {
            storage.index.saveHash(
                key = IndexedFileKey(file.id.value, source.id),
                hash = hash,
            )
        }
    }

    companion object {
        private const val CHUNK_SIZE = 1 * 1024 * 1024 // 1 MB
    }
}
