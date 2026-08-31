package com.fserver.common.exception

/** A device-detection request could not be started or could not finish. */
open class DetectionFailedException(
    message: String? = null,
    cause: Throwable? = null,
) : FServerException(message, cause)

/** The scanned payload was not a connection code this build understands. */
class MalformedQrException : DetectionFailedException()
