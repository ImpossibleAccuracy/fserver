package com.fserver.files.model

sealed class FileSystemException(message: String, cause: Throwable? = null) :
    Exception(message, cause) {

    data class InvalidPath(val path: String) : FileSystemException("Invalid path: $path")
    data class NotDirectory(val path: String) : FileSystemException("Path is not directory: $path")
}
