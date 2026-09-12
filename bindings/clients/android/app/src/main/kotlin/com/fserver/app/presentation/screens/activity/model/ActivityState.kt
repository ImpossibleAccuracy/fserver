package com.fserver.app.presentation.screens.activity.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Sync
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.fserver.app.presentation.composable.model.TransferUi
import com.fserver.app.presentation.screens.source.request.shared.model.SyncRequestUi
import com.fserver.core.sync.progress.FileTransfer

@Immutable
data class ActivityState(
    val freedLabel: String? = null,
    val quotaLabel: String? = null,
    val syncRequest: SyncRequestUi? = null,
    val syncRequestsWaiting: Int = 0,
    val conflicts: List<ConflictUi> = emptyList(),
    val running: List<TransferUi> = emptyList(),
    val history: List<HistoryUi> = emptyList(),
) {
    val hasTotals: Boolean
        get() = freedLabel != null || quotaLabel != null

    val needsAttention: Boolean
        get() = syncRequest != null || conflicts.isNotEmpty()

    val isEmpty: Boolean
        get() = !needsAttention && running.isEmpty() && history.isEmpty()

    @Immutable
    data class ConflictUi(
        val id: String,
        val fileName: String,
        val detail: String,
    )

    @Immutable
    data class HistoryUi(
        val id: String,
        val kind: KindUi,
        val title: String,
        val detail: String,
        val timeLabel: String,
        val undoable: Boolean = false,
    )

    enum class KindUi { Offloaded, Synced, Deleted, Downloaded }

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
                detail = "Kept the Laptop version; yours is in .conflicts",
            ),
        )

        val SampleHistory = listOf(
            HistoryUi(
                id = "offload",
                kind = KindUi.Offloaded,
                title = "Offloaded 84 files",
                detail = "Freed 3.2 GB",
                timeLabel = "14:20",
            ),
            HistoryUi(
                id = "sync",
                kind = KindUi.Synced,
                title = "Documents synced",
                detail = "6 changes",
                timeLabel = "11:05",
            ),
            HistoryUi(
                id = "delete",
                kind = KindUi.Deleted,
                title = "Deleted on Laptop: 2 files",
                detail = "",
                timeLabel = "09:41",
                undoable = true,
            ),
            HistoryUi(
                id = "download",
                kind = KindUi.Downloaded,
                title = "Fetched on demand: IMG_2210",
                detail = "4.8 MB",
                timeLabel = "08:52",
            ),
        )
    }
}

val ActivityState.KindUi.icon: ImageVector
    get() = when (this) {
        ActivityState.KindUi.Offloaded -> Icons.Default.CloudUpload
        ActivityState.KindUi.Synced -> Icons.Default.Sync
        ActivityState.KindUi.Deleted -> Icons.Default.DeleteOutline
        ActivityState.KindUi.Downloaded -> Icons.Default.CloudDownload
    }
