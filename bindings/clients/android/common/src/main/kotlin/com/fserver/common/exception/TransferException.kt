package com.fserver.common.exception

sealed class TransferException(message: String, cause: Throwable? = null) :
    FServerException(message, cause) {

    class UploadNotFoundException(path: String) : TransferException("Upload not found: $path")

    class FileNotFoundException(message: String) : TransferException(message)

    class UploadHashMismatchException(expectedHash: String, actualHash: String) :
        TransferException("Upload hash mismatch: expected $expectedHash, but got $actualHash")

    class TooManyUploadsException(maxUploads: Int) :
        TransferException("Too many uploads in progress: at most $maxUploads at a time")

    class PendingChunksOverflowException(occupiedBytes: Int, maxBytes: Int) :
        TransferException("Pending chunks overflow: occupied $occupiedBytes bytes, but max is $maxBytes bytes")
}
