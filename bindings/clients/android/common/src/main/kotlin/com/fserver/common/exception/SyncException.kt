package com.fserver.common.exception

/**
 * Anything a sync pass throws on purpose.
 * Catch this to mean "the pass broke", a subclass to act on why.
 */
sealed class SyncException(message: String, cause: Throwable? = null) :
    FServerException(message, cause) {

    /** At least one action in the pass failed. Every individual cause is a suppressed exception. */
    class ActionFailedException(message: String, cause: Throwable? = null) :
        SyncException(message, cause)

    /** The pass kept re-planning without converging and was cut off. */
    class MaxRetriesExceededException(message: String, cause: Throwable? = null) :
        SyncException(message, cause)
}
