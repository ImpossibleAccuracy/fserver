package com.fserver.core.store.oneshot

import com.fserver.core.files.SourceLocation
import com.fserver.core.store.FServerStorageApi
import com.fserver.core.oneshot.model.OneShotTransfer
import com.fserver.core.oneshot.model.OneShotTransferFile
import kotlin.time.Instant

/**
 * One-shot transfers, both directions. Persisted so an unanswered ask and a half-received file
 * survive a restart. A finished transfer is final: no call here moves it again.
 */
@SubclassOptInRequired(FServerStorageApi::class)
interface OneShotTransfersStore {
    suspend fun find(id: String): OneShotTransfer?

    /** Pending and active transfers - what the engine still owes work on. Oldest first. */
    suspend fun unfinished(): List<OneShotTransfer>

    /**
     * Records a new transfer with its files. Returns false and changes nothing when [OneShotTransfer.id]
     * is taken: the id comes from the peer, and must never let it overwrite another transfer.
     */
    suspend fun insert(transfer: OneShotTransfer): Boolean

    /**
     * Moves an unfinished transfer to [status], stamping [at] as its end when [status] is final.
     * Returns false when the transfer is gone or already finished.
     */
    suspend fun updateStatus(id: String, status: OneShotTransfer.Status, at: Instant): Boolean

    /**
     * Pending incoming transfer → active, writing into [destination]. Returns false for anything
     * else, so an ask that was cancelled meanwhile stays cancelled.
     */
    suspend fun accept(id: String, destination: SourceLocation.Hostable): Boolean

    /** Replaces the file's locator, progress and status. No-op when the transfer is finished. */
    suspend fun updateFile(transferId: String, file: OneShotTransferFile)

    /** Records that `[0, committedBytes)` is flushed. No-op when the transfer is finished. */
    suspend fun checkpoint(transferId: String, index: Int, committedBytes: Long)
}
