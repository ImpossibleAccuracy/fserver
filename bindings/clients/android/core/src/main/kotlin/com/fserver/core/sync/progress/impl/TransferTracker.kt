package com.fserver.core.sync.progress.impl

import com.fserver.core.sync.progress.FileTransfer
import com.fserver.core.sync.progress.FileTransferKey
import com.fserver.core.util.TimeProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

/** Every file moving either way, planned or not. */
internal class TransferTracker(
    private val timeProvider: TimeProvider,
) {
    private val state = MutableStateFlow<Map<FileTransferKey, FileTransfer>>(emptyMap())

    val transfers: Flow<List<FileTransfer>> =
        state.map { transfers -> transfers.values.sortedBy { it.startedAt } }

    fun clearFinished() {
        state.update { transfers -> transfers.filterValues { !it.isFinished } }
    }

    /** Planned, nothing sent yet. A retry of a file already moving keeps the transfer moving it. */
    fun queued(key: FileTransferKey, path: String, totalBytes: Long) {
        val now = timeProvider.now()

        state.update { transfers ->
            if (transfers[key]?.isFinished == false) return@update transfers

            transfers + (key to FileTransfer(
                key = key,
                path = path,
                totalBytes = totalBytes,
                transferredBytes = 0L,
                state = FileTransfer.State.Queued,
                startedAt = now,
                updatedAt = now,
            ))
        }
    }

    /**
     * Registers the transfer outright, so a file a peer pushed without this device planning it
     * still shows up. A queued entry is replaced: the rate is measured from the first byte.
     */
    fun started(key: FileTransferKey, path: String, totalBytes: Long) {
        val now = timeProvider.now()

        state.update { transfers ->
            transfers + (key to FileTransfer(
                key = key,
                path = path,
                totalBytes = totalBytes,
                transferredBytes = 0L,
                state = FileTransfer.State.Running,
                startedAt = now,
                updatedAt = now,
            ))
        }
    }

    /**
     * Chunks are frame-sized, so a big file reports thousands of times; anything under
     * [ProgressStepBytes] leaves the map untouched and the `StateFlow` therefore silent.
     */
    fun advanced(key: FileTransferKey, transferredBytes: Long) {
        state.update { transfers ->
            val existing = transfers[key] ?: return@update transfers
            if (transferredBytes - existing.transferredBytes < ProgressStepBytes) return@update transfers

            transfers + (key to existing.copy(
                transferredBytes = transferredBytes,
                state = FileTransfer.State.Running,
                updatedAt = timeProvider.now(),
            ))
        }
    }

    fun completed(key: FileTransferKey) {
        update(key) {
            it.copy(
                transferredBytes = maxOf(it.transferredBytes, it.totalBytes),
                state = FileTransfer.State.Completed,
            )
        }

        pruneFinished()
    }

    fun failed(key: FileTransferKey, failure: Throwable?) {
        update(key) { it.copy(state = FileTransfer.State.Failed(failure?.message)) }

        pruneFinished()
    }

    /** No bytes moved, so it is dropped rather than failed. */
    fun skipped(key: FileTransferKey) {
        state.update { it - key }
    }

    /** Whoever moved the bytes already marked it; one still [FileTransfer.State.Queued] moved none. */
    fun settled(key: FileTransferKey) = update(key) { transfer ->
        if (transfer.state == FileTransfer.State.Queued) {
            transfer.copy(state = FileTransfer.State.Completed)
        } else {
            transfer
        }
    }

    /** The list is unbounded otherwise: every completed file would sit in it until shutdown. */
    private fun pruneFinished() {
        state.update { transfers ->
            val finished = transfers.values.filter { it.isFinished }
            if (finished.size <= MaxFinishedTransfers) return@update transfers

            val dropped = finished
                .sortedBy { it.updatedAt }
                .take(finished.size - MaxFinishedTransfers)
                .map { it.key }

            transfers - dropped.toSet()
        }
    }

    private fun update(key: FileTransferKey, transform: (FileTransfer) -> FileTransfer) {
        state.update { transfers ->
            val existing = transfers[key] ?: return@update transfers
            transfers + (key to transform(existing).copy(updatedAt = timeProvider.now()))
        }
    }

    private companion object {
        /** Bytes a transfer must advance by before it is worth waking every collector. */
        const val ProgressStepBytes = 256L * 1024

        const val MaxFinishedTransfers = 100
    }
}
