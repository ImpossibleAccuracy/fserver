package com.fserver.common.exception

/**
 * Anything a sync pass throws on purpose.
 * Catch this to mean "the pass broke", a subclass to act on why.
 */
sealed class SyncException(message: String, cause: Throwable? = null) :
    FServerException(message, cause) {

    class DuplicateSourceException(val sourceId: String, val location: String, val mode: String) :
        SyncException("Source already registered with id: $sourceId for location: $location and mode: $mode")

    /** At least one action in the pass failed. Every individual cause is a suppressed exception. */
    class ActionFailedException(message: String, cause: Throwable? = null) :
        SyncException(message, cause)

    /**
     * The peer answered, and the answer was "no". [message] carries the reason it gave, so a
     * refusal is not reported as a protocol error.
     */
    class RemoteRejectedException(message: String, cause: Throwable? = null) :
        SyncException(message, cause)

    /** The pass kept re-planning without converging and was cut off. */
    class MaxRetriesExceededException(message: String, cause: Throwable? = null) :
        SyncException(message, cause)
}
