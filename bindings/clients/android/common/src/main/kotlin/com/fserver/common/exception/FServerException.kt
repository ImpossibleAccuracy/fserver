package com.fserver.common.exception

/**
 * Root of everything this project throws on purpose.
 * Catch this to mean "one of ours failed", and a narrower type to act on a specific case.
 */
open class FServerException(
    message: String? = null,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
