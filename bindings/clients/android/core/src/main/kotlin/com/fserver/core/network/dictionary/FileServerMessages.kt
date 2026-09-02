package com.fserver.core.network.dictionary

import com.fserver.core.network.dictionary.dto.FileRecordDto
import kotlinx.serialization.Serializable

@Serializable
internal sealed interface FileServerMessages {
    @Serializable
    data class FetchFiles(val sourceId: String) : FileServerMessages

    @Serializable
    data class OperationWithConfirmation(
        val operationId: String,
        val instance: RemoteOperation,
    ) : FileServerMessages

    @Serializable
    class UploadChunk(
        val sourceId: String,
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

        /** [FetchFiles] could not be answered: no such source, not ours to ask for, or busy. */
        @Serializable
        data class FetchFilesFailed(
            val sourceId: String,
            val reason: String,
        ) : Response

        @Serializable
        data class OperationFailed(
            val operationId: String,
            val reason: String,
        ) : Response

        @Serializable
        data class OperationCompleted(
            val operationId: String,
        ) : Response
    }
}
