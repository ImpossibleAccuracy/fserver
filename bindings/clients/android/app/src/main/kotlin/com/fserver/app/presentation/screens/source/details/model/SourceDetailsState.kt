package com.fserver.app.presentation.screens.source.details.model

import com.fserver.app.presentation.model.UiText
import com.fserver.app.presentation.composable.model.PeerUi
import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.screens.source.shared.model.SourceEndpointUi
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.core.network.device.model.DeviceKind

@Immutable
data class SourceDetailsState(
    val isLoading: Boolean = true,
    val label: String = "",
    val status: StatusUi = StatusUi.Active,
    val isSyncing: Boolean = false,

    val mode: SourceModeUi = SourceModeUi.AutoUpload,
    val canSync: Boolean = true,
    val origin: SourceEndpointUi = SourceEndpointUi(),
    val target: SourceEndpointUi = SourceEndpointUi(),
    val conditions: List<ConditionUi> = emptyList(),

    val peer: PeerUi = PeerUi(),

    val stages: List<StageUi> = emptyList(),
    val sendNow: SendNowUi? = null,

    val attention: List<AttentionUi> = emptyList(),
    val history: List<HistoryUi> = emptyList(),
) {
    @Immutable
    data class SendNowUi(
        val count: Int,
        val bytes: Long,
    )

    @Immutable
    sealed interface StatusUi {
        data object Active : StatusUi
        data object Pending : StatusUi
        data class Disabled(val reason: String) : StatusUi
    }

    @Immutable
    sealed interface ConditionUi {
        data class Network(val wifiOnly: Boolean) : ConditionUi
        data object WhileCharging : ConditionUi
        data class IgnoreBefore(val dateLabel: String) : ConditionUi
        data object CopiesStay : ConditionUi
        data class EvictOlderThan(val days: Int) : ConditionUi
        data class EvictLargerThan(val bytes: Long) : ConditionUi
        data class OnConflict(val ask: Boolean) : ConditionUi
        data class MaxFiles(val count: Int) : ConditionUi
        data class MaxSize(val bytes: Long) : ConditionUi
    }

    @Immutable
    data class StageUi(
        val kind: StageKindUi,
        val count: Int,
        val bytes: Long? = null,
        val detail: UiText? = null,
    ) {
        val isWaiting: Boolean
            get() = count > 0 && kind == StageKindUi.Outgoing
    }

    enum class StageKindUi { Here, Outgoing, Matched, Peer, Evicted }

    @Immutable
    sealed interface AttentionUi {
        data class Conflicts(val count: Int, val fileNames: List<String>) : AttentionUi
        data class LostOnPeer(val count: Int, val fileNames: List<String>) : AttentionUi
        data class PeerAlmostFull(
            val usedPercent: Float,
            val files: Int,
            val bytes: Long,
        ) : AttentionUi {
            val usedFraction: Float
                get() = (usedPercent / 100f).coerceIn(0f, 1f)
        }
    }

    @Immutable
    data class HistoryUi(
        val id: String,
        val dateLabel: String,
        val detail: String,
        val timeLabel: String,
        val warning: Boolean = false,
    )

    companion object {
        val SampleHistory = listOf(
            HistoryUi("1", "Today", "Sent 14 · 212 MB", "9:12"),
            HistoryUi("2", "Yesterday", "Sent 36 · 1.1 GB", "21:40"),
            HistoryUi("3", "Sep 23", "Skipped 2 — connection dropped", "18:05", warning = true),
            HistoryUi("4", "Sep 20", "First upload · 3 354 files", "19:02"),
        )

        val SampleAutoUpload = SourceDetailsState(
            isLoading = false,
            label = "Camera",
            isSyncing = true,
            mode = SourceModeUi.AutoUpload,
            origin = SourceEndpointUi(
                name = "Pixel 8",
                detail = UiText.Text("DCIM/Camera"),
                deviceKind = DeviceKind.Phone
            ),
            target = SourceEndpointUi(name = "Server", deviceKind = DeviceKind.Nas),
            peer = PeerUi(id = "server", name = "Server", kind = DeviceKind.Nas, online = true),
            conditions = listOf(
                ConditionUi.Network(wifiOnly = true),
                ConditionUi.CopiesStay,
            ),
            stages = listOf(
                StageUi(StageKindUi.Here, 3432, 19_100_000_000, UiText.Text("Camera")),
                StageUi(StageKindUi.Outgoing, 14, 212_000_000),
                StageUi(StageKindUi.Peer, 3398, 18_200_000_000, UiText.Text("/Photos/Phone")),
            ),
            sendNow = SendNowUi(count = 14, bytes = 212_000_000),
            history = SampleHistory,
        )

        val SampleSync = SourceDetailsState(
            isLoading = false,
            label = "Documents",
            mode = SourceModeUi.Sync,
            origin = SourceEndpointUi(
                name = "Pixel 8",
                detail = UiText.Text("Documents"),
                deviceKind = DeviceKind.Phone
            ),
            target = SourceEndpointUi(name = "Laptop", deviceKind = DeviceKind.Laptop),
            peer = PeerUi(id = "laptop", name = "Laptop", kind = DeviceKind.Laptop, online = true),
            conditions = listOf(
                ConditionUi.Network(wifiOnly = true),
                ConditionUi.OnConflict(ask = true),
                ConditionUi.MaxFiles(1000),
            ),
            stages = listOf(
                StageUi(StageKindUi.Here, 1208, 2_400_000_000, UiText.Text("Documents")),
                StageUi(StageKindUi.Outgoing, 3),
                StageUi(StageKindUi.Matched, 1203),
                StageUi(StageKindUi.Peer, 1211, 2_400_000_000, UiText.Text("~/Documents")),
            ),
            attention = listOf(
                AttentionUi.Conflicts(2, listOf("Lease.docx", "Budget 2026.xlsx")),
            ),
            history = listOf(
                HistoryUi("1", "Today", "↑ 3 · ↓ 5 · 2 conflicts", "9:05", warning = true),
                HistoryUi("2", "Yesterday", "↑ 12 · ↓ 4", "18:22"),
            ),
        )

        val SampleOffload = SourceDetailsState(
            isLoading = false,
            label = "WhatsApp Media",
            mode = SourceModeUi.Offload,
            origin = SourceEndpointUi(
                name = "Pixel 8",
                detail = UiText.Text("WhatsApp/Media"),
                deviceKind = DeviceKind.Phone
            ),
            target = SourceEndpointUi(name = "Server", deviceKind = DeviceKind.Nas),
            peer = PeerUi(id = "server", name = "Server", kind = DeviceKind.Nas, online = true),
            conditions = listOf(
                ConditionUi.Network(wifiOnly = true),
                ConditionUi.EvictOlderThan(30),
            ),
            stages = listOf(
                StageUi(StageKindUi.Here, 1120, 4_600_000_000),
                StageUi(StageKindUi.Outgoing, 40, 1_300_000_000),
                StageUi(StageKindUi.Peer, 8940, 36_000_000_000, UiText.Text("/Backup/WhatsApp")),
                StageUi(StageKindUi.Evicted, 7860, 31_000_000_000),
            ),
            attention = listOf(
                AttentionUi.PeerAlmostFull(
                    usedPercent = 92f,
                    files = 8940,
                    bytes = 36_000_000_000,
                ),
            ),
        )
    }
}
