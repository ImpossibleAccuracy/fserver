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
import com.fserver.core.sync.remote.PeerIndexFetcher
import com.fserver.files.FilesNode
import com.fserver.files.upload.FileAction

/**
 * Streams one local file to the peer: [RemoteOperation.Upload.Init], chunks, then
 * [RemoteOperation.Upload.UploadCompleted] with the hash. Split out of [FileActionRunner] because it
 * is the one action with a multi-message protocol of its own.
 */
internal class FileUploader(
    private val remoteFetcher: PeerIndexFetcher,
    private val storage: FServerStorage,
    private val node: FilesNode,
) {
    suspend fun uploadFile(
        action: FileAction.Upload,
        source: SourceEntry,
    ) {
        val locator = action.file.locator
            ?: error("Cannot upload file ${action.file.id} because it has no locator")

        val session = remoteFetcher.connectToDevice(source)

        // Run as operation to confirm that the peer is ready to receive the file
        session.runRemoteOperation(
            operation = RemoteOperation.Upload.Init(action.file.toDto())
        )

        val hasher = if (action.file.content == null) FileHasher() else null

        val fs = node.openSource(source.location.toFiles())
        val file = fs.openFile(locator)

        file.use { steam ->
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
                        fileId = action.file.id.value,
                        offset = offset,
                        bytes = chunk,
                    )
                ).getOrThrow()

                offset += bytesRead
            }
        }

        val hash = hasher?.compute() ?: action.file.content!!

        session.runRemoteOperation(
            operation = RemoteOperation.Upload.UploadCompleted(
                fileId = action.file.id.value,
                hash = hash.value,
                algorithm = hash.algorithm,
            )
        )

        if (hasher != null) {
            storage.index.saveHash(
                fileId = IndexedFileKey(action.file.id.value, source.id),
                hash = hash,
            )
        }
    }

    companion object {
        private const val CHUNK_SIZE = 1 * 1024 * 1024 // 1 MB
    }
}
