package com.fserver.core.network.dictionary

import com.fserver.core.network.dictionary.dto.FileRecordDto
import com.fserver.core.network.dictionary.dto.OneShotFileDto
import com.fserver.core.network.dictionary.dto.UploadKey
import com.fserver.core.network.dictionary.dto.SourceMetadataDto
import com.fserver.core.network.dictionary.dto.SyncModeDto
import com.fserver.core.sync.progress.SyncFailureReason
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

    /**
     * Asks the peer to run a pass over [sourceId], sent where only the peer drives it. Fire and
     * forget: the pass is the peer's to schedule, and a lost request waits for the next trigger.
     */
    @Serializable
    data class RequestSync(val sourceId: String) : FileServerMessages

    /** Asks the peer to hold the pass over [Request.sourceId] while we run ours. */
    @Serializable
    sealed interface AcquireSyncLease : FileServerMessages {
        /**
         * Request a lease for [sourceId]. [syncMode] is the requester's, checked against the peer's before a grant.
         * [metadata] is the requester's half, sent only where it reports it - see `sharesMetadata`.
         */
        @Serializable
        data class Request(
            val sourceId: String,
            val leaseId: String,
            val syncMode: SyncModeDto,
            val metadata: SourceMetadataDto? = null,
        ) : AcquireSyncLease

        /** Gives [leaseId] back. Fire and forget: the holder's TTL and session end cover a lost one. */
        @Serializable
        data class ReleaseLease(
            val sourceId: String,
            val leaseId: String,
            val failure: SyncFailureReason? = null,
        ) : FileServerMessages

        /** [metadata] is the granting side's half, same rule as [Request.metadata]. */
        @Serializable
        data class Granted(
            val sourceId: String,
            val leaseId: String,
            val metadata: SourceMetadataDto? = null,
        ) : AcquireSyncLease, Response

        /** [Request] refused for now: the source is already being synced, or is not ours to ask for. */
        @Serializable
        data class Denied(
            val sourceId: String,
            val reason: String,
        ) : AcquireSyncLease, Response

        /**
         * [Request] refused: the requester's mode is stale. [syncMode] is the initiator's, which the
         * requester adopts before asking again.
         */
        @Serializable
        data class Outdated(
            val sourceId: String,
            val syncMode: SyncModeDto,
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
         * @property metadata the sender's half. Informational only.
         */
        @Serializable
        data class Request(
            val sourceId: String,
            val label: String,
            val syncMode: SyncModeDto,
            val metadata: SourceMetadataDto,
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
     * Pushes one file: [Init], then [UploadChunk]s, then [Complete], with [Status] between chunks.
     * The same for every [UploadKey] - a file of a source, or of a one-shot transfer; only where the
     * receiver puts it differs.
     *
     * The receiver stages the bytes and keeps them across a dropped session, so every request is
     * answered with [Received] - where to send from - and a sender resumes instead of restarting.
     */
    @Serializable
    sealed interface Upload : FileServerMessages {
        val key: UploadKey

        /**
         * Opens the upload, or picks up the one this file already has staged. [file] describes it
         * for a source; a one-shot file was described by its offer, and carries none.
         */
        @Serializable
        data class Init(
            override val key: UploadKey,
            val file: FileRecordDto? = null,
        ) : Upload

        /** Asks how far the receiver got. [Failed] means the upload is gone: [Init] it again. */
        @Serializable
        data class Status(override val key: UploadKey) : Upload

        /** All chunks sent. Answered [Completed], or [Received] when bytes are missing. */
        @Serializable
        data class Complete(
            override val key: UploadKey,
            val hash: String,
            val algorithm: String,
        ) : Upload

        /** The sender gives up on this file - it cannot read it anymore. Answered [Completed]. */
        @Serializable
        data class Abandon(
            override val key: UploadKey,
            val reason: String,
        ) : Upload

        /** The receiver durably holds `[0, offset)`: the sender goes on from [offset]. */
        @Serializable
        data class Received(
            override val key: UploadKey,
            val offset: Long,
        ) : Upload, Response

        /** The file is in place - or, answering [Init], already was. */
        @Serializable
        data class Completed(override val key: UploadKey) : Upload, Response

        /** Answers [Init]: the receiver's own file limits have no room for this new file. Skip it, do not retry. */
        @Serializable
        data class OverLimit(override val key: UploadKey) : Upload, Response

        /** This attempt failed: [Init] again. */
        @Serializable
        data class Failed(
            override val key: UploadKey,
            val reason: String,
        ) : Upload, Response

        /** Whatever the key belongs to takes nothing more - a one-shot transfer cancelled or settled. Stop. */
        @Serializable
        data class Stopped(
            override val key: UploadKey,
            val reason: String,
        ) : Upload, Response
    }

    /**
     * Upload single chunk of file.
     *
     * @see Upload
     */
    @Serializable
    class UploadChunk(
        val key: UploadKey,
        val offset: Long,
        val bytes: ByteArray,
    ) : FileServerMessages

    /**
     * A one-shot transfer's setup, outside any source. Not request/[Response] pairs, same reason as
     * [ConfigureSource]: the answer waits on the receiving user, and a cancel on either one.
     */
    @Serializable
    sealed interface OneShot : FileServerMessages {
        /**
         * @property transferId chosen by the sender. Both sides hold the transfer under it.
         * @property senderName for display only: unauthenticated, never identity.
         */
        @Serializable
        data class Offer(
            val transferId: String,
            val senderName: String,
            val files: List<OneShotFileDto>,
        ) : OneShot

        /** The receiving user's answer. Final either way. */
        @Serializable
        data class Decision(
            val transferId: String,
            val accepted: Boolean,
            val reason: String? = null,
        ) : OneShot

        /** Either side stopped it. Final. */
        @Serializable
        data class Cancel(
            val transferId: String,
            val reason: String? = null,
        ) : OneShot
    }

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
