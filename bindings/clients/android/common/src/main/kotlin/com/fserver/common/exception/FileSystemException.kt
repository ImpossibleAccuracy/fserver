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
    class DeleteRejected(locator: String) : FileSystemException("Delete rejected: $locator")

    /** A file sealed at rest by a method this device does not have. */
    class UnknownCipher(val cipherId: String) : FileSystemException("Unknown storage cipher: $cipherId")

    /** A file sealed at rest under a key this device no longer has. */
    class MissingKey(val keyId: String) : FileSystemException("Missing storage key: $keyId")

    /** A file sealed at rest that is damaged or was tampered with. Its bytes are never handed out. */
    class Corrupted(reason: String, cause: Throwable? = null) :
        FileSystemException("Corrupted sealed file: $reason", cause)
}
