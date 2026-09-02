package com.fserver.core.network.dictionary

import com.fserver.core.network.dictionary.dto.FileRecordDto
import com.fserver.core.sync.index.IndexedFileKey
import kotlinx.serialization.Serializable

/**
 * What one peer asks another to do. Travels inside [FileServerMessages.OperationWithConfirmation.Request],
 * which pairs it with the id the peer's [FileServerMessages.OperationWithConfirmation.Completed] is matched by.
 */
@Serializable
internal sealed interface RemoteOperation {
    /** Acts on a file the peer already knows from its own index. */
    @Serializable
    sealed interface File : RemoteOperation {
        val key: IndexedFileKey

        @Serializable
        data class Hash(override val key: IndexedFileKey) : File

        @Serializable
        data class Delete(override val key: IndexedFileKey) : File

        @Serializable
        data class Download(override val key: IndexedFileKey) : File
    }

    /** Brackets a [FileServerMessages.UploadChunk] stream: [Init] before the first chunk, [UploadCompleted] after the last. */
    @Serializable
    sealed interface Upload : RemoteOperation {
        @Serializable
        data class Init(
            val sourceId: String,
            val file: FileRecordDto
        ) : Upload

        @Serializable
        data class UploadCompleted(
            val key: IndexedFileKey,
            val hash: String,
            val algorithm: String,
        ) : Upload
    }
}
