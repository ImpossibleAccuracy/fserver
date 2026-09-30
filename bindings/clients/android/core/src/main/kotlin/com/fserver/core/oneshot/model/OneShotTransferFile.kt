package com.fserver.core.oneshot.model

data class OneShotTransferFile(
    /** Position in the transfer. Identifies the file on both sides. */
    val index: Int,
    /** Display name the sender reported. Never a path to write to as is. */
    val name: String,
    val size: Long,
    /** Outgoing: what the sender reads. Incoming: where it is written; null until created. */
    val locator: String?,
    /** `[0, committedBytes)` is flushed on the receiving side. */
    val committedBytes: Long = 0,
    val status: Status = Status.Pending,
) {
    init {
        require(index >= 0) { "Negative index $index" }
        require(size >= 0) { "Negative size of #$index" }
        require(committedBytes in 0..size) { "Committed $committedBytes of $size in #$index" }
    }

    sealed interface Status {
        data object Pending : Status

        /** Whole and checked against the sender's hash. */
        data object Completed : Status

        data class Failed(val reason: String) : Status
    }
}