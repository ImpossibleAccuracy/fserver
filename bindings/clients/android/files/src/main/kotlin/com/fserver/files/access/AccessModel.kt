package com.fserver.files.access

import com.fserver.files.model.ScanSource
import kotlinx.coroutines.flow.Flow

interface AccessModel {
    fun supports(params: Params): Boolean

    fun upload(params: Params, directory: ScanSource): Flow<UploadProgress>

    interface Params

    sealed interface UploadProgress {
        data object Completed : UploadProgress
        data class Error(val message: String) : UploadProgress
        data class Progress(val bytesUploaded: Long, val totalBytes: Long) : UploadProgress
    }
}
