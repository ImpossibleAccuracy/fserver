package com.fserver.core.network.dictionary

import com.fserver.core.network.dictionary.dto.FileRecordDto
import kotlinx.serialization.Serializable

@Serializable
internal sealed interface FileServerMessages {
    @Serializable
    data object FetchFiles : FileServerMessages

    @Serializable
    data class OperationWithConfirmation(
        val operationId: String,
        val instance: RemoteOperation,
    ) : FileServerMessages

    @Serializable
    class UploadChunk(
        val fileId: String,
        val offset: Long,
        val bytes: ByteArray,
    ) : FileServerMessages

    @Serializable
    sealed interface Response : FileServerMessages {
        @Serializable
        data class FilesList(
            val files: List<FileRecordDto>,
        ) : Response

        @Serializable
        data class OperationCompleted(
            val operationId: String,
        ) : Response
    }
}
