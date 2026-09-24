package com.fserver.app.presentation.screens.dashboard.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.screens.source.request.shared.model.SyncRequestUi
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.core.network.TransportKind
import com.fserver.core.network.device.model.DeviceKind

@Immutable
data class DashboardState(
    val devices: List<DeviceUi> = emptyList(),
    val expandedDevice: DeviceDetailsUi? = null,
    val syncRequest: SyncRequestUi? = null,
    val syncRequestsWaiting: Int = 0,
    val syncRequestHintDismissed: Boolean = false,
    val networkWarning: NetworkWarningUi? = null,
) {
    val showsSyncRequestHint: Boolean
        get() = syncRequest != null && !syncRequestHintDismissed

    @Immutable
    data class DeviceUi(
        val id: String,
        val name: String,
        val kind: DeviceKind?,
        val online: Boolean,
        val itemCount: Int,
        val unreachable: Boolean = false,
    )

    @Immutable
    data class DeviceDetailsUi(
        val id: String,
        val name: String,
        val kind: DeviceKind?,
        val online: Boolean,
        val addressLabel: String?,
        val fingerprintLabel: String?,
        val foundBy: TransportKind?,
        val lastSeenLabel: String?,
        val folders: List<FolderUi>,
        val unreachable: UnreachableUi? = null,
    )

    @Immutable
    data class UnreachableUi(
        val reason: ReasonUi,
        val triedLabel: String?,
        val transport: TransportKind?,
        val onOtherNetwork: Boolean,
    )

    enum class ReasonUi { NoRoute, Unreachable, Refused, NotAllowed, Failed }

    @Immutable
    data class FolderUi(
        val id: String,
        val name: String,
        val path: String?,
        val mode: SourceModeUi,
        val status: FolderStatusUi,
        val statusDetail: String? = null,
        val itemCount: Int = 0,
        val progress: Float? = null,
    ) {
        val accented: Boolean
            get() = status == FolderStatusUi.Syncing
    }

    enum class FolderStatusUi { Pending, Active, Syncing, Disabled }

    /**
     * [UnnamedNetwork] is not a worse network, it is a network the app cannot identify: Android
     * answers an ungranted SSID/BSSID read with a redacted placeholder rather than a refusal, so
     * [DifferentNetwork] would be a guess. It is the only one with something to tap.
     */
    enum class NetworkWarningUi {
        NoNetwork,
        NoLocalNetwork,
        DifferentNetwork,
        UnnamedNetwork;

        val isActionable: Boolean get() = this == UnnamedNetwork
    }

    companion object {
        val SampleDevices = listOf(
            DeviceUi("laptop", "Laptop", DeviceKind.Laptop, online = true, itemCount = 912),
            DeviceUi("server", "Server", DeviceKind.Nas, online = true, itemCount = 312),
            DeviceUi(
                id = "home-pc",
                name = "Home PC",
                kind = DeviceKind.Desktop,
                online = false,
                itemCount = 74,
                unreachable = true,
            ),
        )

        val SampleFolders = listOf(
            FolderUi(
                id = "camera",
                name = "Camera",
                path = "/Camera",
                mode = SourceModeUi.Offload,
                status = FolderStatusUi.Syncing,
                itemCount = 240,
                progress = 0.4f,
            ),
            FolderUi(
                id = "documents",
                name = "Documents",
                path = "/Documents",
                mode = SourceModeUi.Sync,
                status = FolderStatusUi.Active,
                statusDetail = "yesterday",
                itemCount = 62,
            ),
            FolderUi(
                id = "movies",
                name = "Movies",
                path = null,
                mode = SourceModeUi.AutoUpload,
                status = FolderStatusUi.Disabled,
                statusDetail = "declined by the peer",
            ),
        )

        fun sampleDetailsOf(device: DeviceUi): DeviceDetailsUi = DeviceDetailsUi(
            id = device.id,
            name = device.name,
            kind = device.kind,
            online = device.online,
            addressLabel = "192.168.1.40".takeIf { device.online },
            fingerprintLabel = "9f2c 4a01 b7d3 e820",
            foundBy = TransportKind.MulticastDns,
            lastSeenLabel = "yesterday",
            folders = SampleFolders,
            unreachable = UnreachableUi(
                reason = ReasonUi.Unreachable,
                triedLabel = "5 minutes ago",
                transport = TransportKind.MulticastDns,
                onOtherNetwork = true,
            ).takeIf { device.unreachable },
        )
    }
}
