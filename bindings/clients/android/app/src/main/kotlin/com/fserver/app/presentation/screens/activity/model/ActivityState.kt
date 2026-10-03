package com.fserver.app.presentation.screens.activity.model

import com.fserver.core.crypto.EncryptionProgress
import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.composable.model.TransferUi
import com.fserver.app.presentation.screens.source.request.shared.model.SyncRequestUi
import com.fserver.app.presentation.shared.journal.model.JournalEntryUi
import com.fserver.app.presentation.shared.journal.model.JournalKindUi
import com.fserver.core.sync.progress.FileTransfer

@Immutable
data class ActivityState(
    val freedLabel: String? = null,
    val quotaLabel: String? = null,
    val syncRequest: SyncRequestUi? = null,
    val syncRequestsWaiting: Int = 0,
    val conflicts: List<ConflictUi> = emptyList(),
    val issues: List<JournalEntryUi> = emptyList(),
    val running: List<TransferUi> = emptyList(),
    val queued: QueuedUi? = null,
    val encryption: EncryptionUi? = null,
    val interrupted: List<TransferUi.Interrupted> = emptyList(),
    val history: List<HistoryUi> = emptyList(),
) {
    val hasTotals: Boolean
        get() = freedLabel != null || quotaLabel != null

    val needsAttention: Boolean
        get() = syncRequest != null || conflicts.isNotEmpty() || issues.isNotEmpty()

    val isEmpty: Boolean
        get() = !needsAttention && !isMoving && history.isEmpty()

    val isMoving: Boolean
        get() = running.isNotEmpty() || queued != null || interrupted.isNotEmpty() || encryption != null

    @Immutable
    data class EncryptionUi(
        val done: Int,
        val total: Int,
        val towards: Set<EncryptionProgress.Toward>,
    ) {
        val progress: Float
            get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
    }

    @Immutable
    data class QueuedUi(
        val outgoing: Int,
        val incoming: Int,
        val bytes: Long,
    ) {
        val count: Int
            get() = outgoing + incoming
    }

    @Immutable
    data class ConflictUi(
        val id: String,
        val fileName: String,
        val peerName: String,
        val change: ChangeUi = ChangeUi.EditedBoth,
        val canKeepMine: Boolean = true,
        val canKeepTheirs: Boolean = true,
        val canKeepBoth: Boolean = true,
    )

    enum class ChangeUi { EditedBoth, DeletedHere, DeletedThere }

    @Immutable
    data class HistoryUi(
        val entry: JournalEntryUi,
        val undoable: Boolean = false,
    )

    companion object {
        val SampleRunning = listOf(
            TransferUi.Batch(
                id = "batch-server",
                fileName = "IMG_4482.heic",
                direction = FileTransfer.Direction.Outgoing,
                peerName = "Server",
                doneCount = 14,
                totalCount = 38,
                progress = 0.37f,
                bytesPerSecond = 6_400_000,
            ),
        )

        val SampleEncryption = EncryptionUi(
            done = 120,
            total = 340,
            towards = setOf(EncryptionProgress.Toward.Encrypted),
        )

        val SampleQueued = QueuedUi(outgoing = 24, incoming = 3, bytes = 412_000_000)

        val SampleInterrupted = listOf(
            TransferUi.Interrupted(
                id = "video-03",
                fileName = "video_03.mp4",
                direction = FileTransfer.Direction.Incoming,
                stoppedAtPercent = 62,
            ),
        )

        val SampleConflicts = listOf(
            ConflictUi(
                id = "notes",
                fileName = "Notes.md",
                peerName = "Laptop",
            ),
            ConflictUi(
                id = "budget",
                fileName = "Budget 2026.xlsx",
                peerName = "Laptop",
                change = ChangeUi.DeletedThere,
                canKeepBoth = false,
            ),
        )

        val SampleHistory = JournalEntryUi.Samples
            .filterNot { it.isOpenIssue }
            .map { HistoryUi(it, undoable = it.kind == JournalKindUi.Deleted) }

        val SampleIssues = JournalEntryUi.Samples.filter { it.isOpenIssue }
    }
}

