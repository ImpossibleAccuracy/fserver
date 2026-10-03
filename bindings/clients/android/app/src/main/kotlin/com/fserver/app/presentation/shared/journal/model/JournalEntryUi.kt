package com.fserver.app.presentation.shared.journal.model

import androidx.annotation.StringRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallSplit
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Tune
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.vector.ImageVector
import com.fserver.app.R
import com.fserver.app.presentation.model.UiText
import kotlin.time.Instant

@Immutable
data class JournalEntryUi(
    val id: Long,
    val group: JournalGroupUi,
    val kind: JournalKindUi,
    val title: UiText,
    val details: List<UiText> = emptyList(),
    val at: Instant,
    val issue: IssueUi? = null,
    val sourceId: String? = null,
    val deviceId: String? = null,
) {
    val isOpenIssue: Boolean
        get() = issue?.open == true

    @Immutable
    data class IssueUi(
        val open: Boolean,
        val occurrences: Int,
    )

    companion object {
        private val SampleAt = Instant.fromEpochSeconds(1_790_000_000)

        val Samples = listOf(
            JournalEntryUi(
                id = 1,
                group = JournalGroupUi.Problems,
                kind = JournalKindUi.Clock,
                title = UiText.Text("Laptop's clock is off"),
                details = listOf(UiText.Text("Ahead by 04:12")),
                at = SampleAt,
                issue = IssueUi(open = true, occurrences = 3),
            ),
            JournalEntryUi(
                id = 2,
                group = JournalGroupUi.Sync,
                kind = JournalKindUi.Offloaded,
                title = UiText.Text("WhatsApp Media offloaded"),
                details = listOf(UiText.Text("↑ 84"), UiText.Text("Offloaded: 84")),
                at = SampleAt,
                sourceId = "whatsapp",
            ),
            JournalEntryUi(
                id = 3,
                group = JournalGroupUi.Sync,
                kind = JournalKindUi.Synced,
                title = UiText.Text("Documents synced"),
                details = listOf(UiText.Text("↑ 3"), UiText.Text("↓ 5"), UiText.Text("Deleted on Laptop: 2")),
                at = SampleAt,
                sourceId = "documents",
            ),
            JournalEntryUi(
                id = 4,
                group = JournalGroupUi.Sync,
                kind = JournalKindUi.Fetched,
                title = UiText.Text("Fetched on demand: IMG_2210.jpg"),
                details = listOf(UiText.Text("4.8 MB")),
                at = SampleAt,
                sourceId = "camera",
            ),
        )
    }
}

enum class JournalGroupUi { Sync, Problems, Conflicts, Sources, Encryption, Devices, Transfers }

enum class JournalKindUi { Synced, Offloaded, Deleted, Fetched, Failed, Conflict, Clock, Link, Device, Source, Settings, Encryption, Transfer }

@get:StringRes
val JournalGroupUi.labelRes: Int
    get() = when (this) {
        JournalGroupUi.Sync -> R.string.journal_group_sync
        JournalGroupUi.Problems -> R.string.journal_group_problems
        JournalGroupUi.Conflicts -> R.string.journal_group_conflicts
        JournalGroupUi.Sources -> R.string.journal_group_sources
        JournalGroupUi.Encryption -> R.string.journal_group_encryption
        JournalGroupUi.Devices -> R.string.journal_group_devices
        JournalGroupUi.Transfers -> R.string.journal_group_transfers
    }

val JournalKindUi.icon: ImageVector
    get() = when (this) {
        JournalKindUi.Synced -> Icons.Default.Sync
        JournalKindUi.Offloaded -> Icons.Default.CloudUpload
        JournalKindUi.Deleted -> Icons.Default.DeleteOutline
        JournalKindUi.Fetched -> Icons.Default.CloudDownload
        JournalKindUi.Failed -> Icons.Default.ErrorOutline
        JournalKindUi.Conflict -> Icons.AutoMirrored.Filled.CallSplit
        JournalKindUi.Clock -> Icons.Default.Schedule
        JournalKindUi.Link -> Icons.Default.LinkOff
        JournalKindUi.Device -> Icons.Default.Devices
        JournalKindUi.Source -> Icons.Default.FolderOpen
        JournalKindUi.Settings -> Icons.Default.Tune
        JournalKindUi.Encryption -> Icons.Default.Lock
        JournalKindUi.Transfer -> Icons.Default.SwapVert
    }

val JournalGroupUi.icon: ImageVector
    get() = when (this) {
        JournalGroupUi.Sync -> Icons.Default.Sync
        JournalGroupUi.Problems -> Icons.Default.ErrorOutline
        JournalGroupUi.Conflicts -> Icons.AutoMirrored.Filled.CallSplit
        JournalGroupUi.Sources -> Icons.Default.FolderOpen
        JournalGroupUi.Encryption -> Icons.Default.Lock
        JournalGroupUi.Devices -> Icons.Default.Devices
        JournalGroupUi.Transfers -> Icons.Default.SwapVert
    }
