package com.fserver.core.network.dictionary

import com.fserver.core.network.dictionary.dto.FileRecordDto
import kotlinx.serialization.Serializable

/**
 * What one peer asks another to do. Travels inside [FileServerMessages.OperationWithConfirmation],
 * which pairs it with the id the peer's [FileServerMessages.Response.OperationCompleted] is matched by.
 */
@Serializable
internal sealed interface RemoteOperation {
    /** Acts on a file the peer already knows from its own index. */
    @Serializable
    sealed interface File : RemoteOperation {
        @Serializable
        data class Hash(val fileId: String) : File

        @Serializable
        data class Delete(val fileId: String) : File

        @Serializable
        data class Download(val fileId: String) : File
    }

    /** Brackets a [FileServerMessages.UploadChunk] stream: [Init] before the first chunk, [UploadCompleted] after the last. */
    @Serializable
    sealed interface Upload : RemoteOperation {
        @Serializable
        data class Init(val file: FileRecordDto) : Upload

        @Serializable
        data class UploadCompleted(
            val fileId: String,
            val hash: String,
            val algorithm: String
        ) : Upload
    }
}
