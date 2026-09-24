package com.fserver.common.exception

/**
 * Everything the filesystem layer throws.
 *
 * Shared rather than mirrored: this is the one `:files` throws and the one a host catches, so
 * there is nothing to translate at the `:core` boundary.
 */
sealed class FileSystemException(message: String, cause: Throwable? = null) :
    FServerException(message, cause) {

    class InvalidPath(val path: String) : FileSystemException("Invalid path: $path")
    class NotDirectory(val path: String) : FileSystemException("Path is not directory: $path")
    class AlreadyExists(path: String) : FileSystemException("File already exists at path: $path")
    class CreationFailed(path: String) : FileSystemException("Failed to create file at path: $path")
    class RenameRejected(locator: String, newName: String) :
        FileSystemException("Rename rejected: $locator -> $newName")
}
