package com.fserver.files.access

import com.fserver.common.task.ProgressTask
import com.fserver.files.model.ScanSource
import java.io.File

/**
 * How a source's bytes actually move. Not to be confused with `:core`'s `AccessModel`, which is
 * the policy deciding whether they may move at all.
 */
interface UploadStrategy {
    fun supports(params: Params): Boolean

    // TODO: File is temporary
    fun upload(params: Params, directory: ScanSource): ProgressTask<UploadProgress, List<File>>

    interface Params

    sealed interface UploadProgress {
        data object Completed : UploadProgress
        data class Error(val message: String) : UploadProgress
        data class Progress(val bytesUploaded: Long, val totalBytes: Long) : UploadProgress
    }
}
