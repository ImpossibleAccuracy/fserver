package com.fserver.core.network.dictionary

import com.fserver.core.network.dictionary.dto.FileRecordDto
import kotlinx.serialization.Serializable

@Serializable
internal sealed interface FileServerMessages {
    /** Answer to a request. `:net` matches one to its request by correlation id. */
    @Serializable
    sealed interface Response : FileServerMessages

    /** Asks the peer for its index of [Request.sourceId]. */
    @Serializable
    sealed interface FetchFiles : FileServerMessages {
        @Serializable
        data class Request(val sourceId: String) : FetchFiles

        @Serializable
        data class FilesList(
            val files: List<FileRecordDto>,
        ) : FetchFiles, Response

        /** [Request] could not be answered: no such source, not ours to ask for, or busy. */
        @Serializable
        data class Failed(
            val sourceId: String,
            val reason: String,
        ) : FetchFiles, Response
    }

    /**
     * Asks the peer to hold the pass over [Request.sourceId] while we run ours.
     *
     * Both devices may kick off a pass at the same moment, and a pass plans from a snapshot of
     * both indexes - two of them at once plan against state the other is already changing. Whoever
     * holds the lease runs; the other side skips the source for this round.
     */
    @Serializable
    sealed interface AcquireSyncLease : FileServerMessages {
        /**
         * @property deviceId the sender, so the peer can break a tie deterministically when it is
         * asking for the very same lease at the same time.
         */
        @Serializable
        data class Request(
            val sourceId: String,
            val deviceId: String,
            val leaseId: String,
        ) : AcquireSyncLease

        @Serializable
        data class Granted(
            val sourceId: String,
            val leaseId: String,
        ) : AcquireSyncLease, Response

        /** [Request] refused: the source is already being synced, or is not ours to ask for. */
        @Serializable
        data class Denied(
            val sourceId: String,
            val reason: String,
        ) : AcquireSyncLease, Response
    }

    /** Gives [leaseId] back. Fire and forget: the holder's TTL and session end cover a lost one. */
    @Serializable
    data class ReleaseSyncLease(
        val sourceId: String,
        val leaseId: String,
    ) : FileServerMessages

    @Serializable
    class UploadChunk(
        val sourceId: String,
        val fileId: String,
        val offset: Long,
        val bytes: ByteArray,
    ) : FileServerMessages

    /** Runs [Request.instance] on the peer and reports back under [Request.operationId]. */
    @Serializable
    sealed interface OperationWithConfirmation : FileServerMessages {
        @Serializable
        data class Request(
            val operationId: String,
            val instance: RemoteOperation,
        ) : OperationWithConfirmation

        @Serializable
        data class Completed(
            val operationId: String,
        ) : OperationWithConfirmation, Response

        @Serializable
        data class Failed(
            val operationId: String,
            val reason: String,
        ) : OperationWithConfirmation, Response
    }
}
