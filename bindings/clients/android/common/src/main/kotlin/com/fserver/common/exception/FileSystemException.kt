package com.fserver.common.exception

/**
 * Everything the filesystem layer throws.
 *
 * Shared rather than mirrored: this is the one `:files` throws and the one a host catches, so
 * there is nothing to translate at the `:core` boundary.
 */
sealed class FileSystemException(message: String, cause: Throwable? = null) :
    FServerException(message, cause) {

    data class InvalidPath(val path: String) : FileSystemException("Invalid path: $path")
    data class NotDirectory(val path: String) : FileSystemException("Path is not directory: $path")
}
