package com.fserver.core.network.dictionary

import com.fserver.core.network.dictionary.dto.FileRecordDto
import com.fserver.core.network.dictionary.dto.SyncModeDto
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
     * This device's whole index for [sourceId], pushed once its pass is done.
     *
     * Deliberately not a request/[Response] pair, and deliberately not the answer to anything: the
     * receiver's copy is a cache, so a push that never lands costs it a [FetchFiles] round trip and
     * nothing more. The sender does not wait for it and does not retry.
     */
    @Serializable
    data class PublishIndex(
        val sourceId: String,
        val files: List<FileRecordDto>,
    ) : FileServerMessages

    /** Asks the peer to hold the pass over [Request.sourceId] while we run ours. */
    @Serializable
    sealed interface AcquireSyncLease : FileServerMessages {
        /** Request a lease for [sourceId]. */
        @Serializable
        data class Request(
            val sourceId: String,
            val leaseId: String,
        ) : AcquireSyncLease

        /** Gives [leaseId] back. Fire and forget: the holder's TTL and session end cover a lost one. */
        @Serializable
        data class ReleaseLease(
            val sourceId: String,
            val leaseId: String,
        ) : FileServerMessages

        @Serializable
        data class Granted(
            val sourceId: String,
            val leaseId: String,
        ) : AcquireSyncLease, Response

        /** [Request] refused for now: the source is already being synced, or is not ours to ask for. */
        @Serializable
        data class Denied(
            val sourceId: String,
            val reason: String,
        ) : AcquireSyncLease, Response

        /** [Request] refused for good: this device dropped or disabled it's half of the source. */
        @Serializable
        data class Inactive(
            val sourceId: String,
            val reason: String,
        ) : AcquireSyncLease, Response
    }

    /**
     * Pairs a source across the two devices that sync it:
     * sender registered it's half and asks the receiver to register the other.
     *
     * Deliberately not a request/[Response] pair. The answer needs the receiving user, who may be
     * hours away, so the request is parked and the verdict travels back later as its own
     * [ConfigureSource.Decision].
     */
    @Serializable
    sealed interface ConfigureSource : FileServerMessages {
        /**
         * @property sourceId chosen by the sender. Both halves of a source answer to the same id.
         * @property syncMode what the sender runs the source under.
         */
        @Serializable
        data class Request(
            val sourceId: String,
            val label: String,
            val syncMode: SyncModeDto,
        ) : ConfigureSource

        /** The receiving user's answer. A rejection is final: the asking side drops its half. */
        @Serializable
        data class Decision(
            val sourceId: String,
            val accepted: Boolean,
            val reason: String? = null,
        ) : ConfigureSource
    }

    /**
     * Upload single chunk of file.
     *
     * @see RemoteOperation.Upload
     */
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

        /** Operation completed successfully */
        @Serializable
        data class Completed(
            val operationId: String,
        ) : OperationWithConfirmation, Response

        /** Operation failed to complete */
        @Serializable
        data class Failed(
            val operationId: String,
            val reason: String,
        ) : OperationWithConfirmation, Response
    }
}
