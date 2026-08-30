package com.fserver.core.files.access

import com.fserver.core.files.model.FoundDirectory
import kotlinx.coroutines.flow.Flow

interface AccessModel {
    fun supports(params: Params): Boolean

    fun upload(params: Params, directory: FoundDirectory): Flow<UploadProgress>

    interface Params

    sealed interface UploadProgress {
        data object Completed : UploadProgress
        data class Error(val message: String) : UploadProgress
        data class Progress(val bytesUploaded: Long, val totalBytes: Long) : UploadProgress
    }
}
