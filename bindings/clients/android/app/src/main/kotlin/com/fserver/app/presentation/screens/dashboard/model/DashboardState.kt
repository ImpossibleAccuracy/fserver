package com.fserver.app.presentation.screens.dashboard.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.core.network.device.model.DeviceKind

@Immutable
data class DashboardState(
    val storage: StorageUi? = null,
    val links: List<LinkUi> = emptyList(),
    val network: NetworkUi? = null,
    /** Some discovery method is scanning right now. */
    val discovering: Boolean = false,
    val devices: List<DeviceUi> = emptyList(),
    val syncRequestsWaiting: Int = 0,
    val networkWarning: NetworkWarningUi? = null,
) {
    /** Storage and links only mean something once at least one source exists. */
    val hasLinks: Boolean
        get() = links.isNotEmpty()

    val onlineDevices: Int
        get() = devices.count { it.online }

    @Immutable
    data class StorageUi(
        val totalBytes: Long,
        val freeBytes: Long,
        val appBytes: Long,
    ) {
        val usedBytes: Long
            get() = (totalBytes - freeBytes).coerceAtLeast(0)

        val otherBytes: Long
            get() = (usedBytes - appBytes).coerceAtLeast(0)
    }

    /** One source, read as "what goes where". */
    @Immutable
    data class LinkUi(
        val id: String,
        val label: String,
        val deviceName: String,
        val deviceKind: DeviceKind?,
        val mode: SourceModeUi,
        /** Files leave this phone; false when the peer is the one sending. */
        val outgoing: Boolean,
        val status: LinkStatusUi,
        /** When it last synced, or why it is disabled. */
        val statusDetail: String? = null,
        val progress: Float? = null,
    )

    enum class LinkStatusUi { Pending, Active, Syncing, Disabled }

    /** The link this phone is on. [name] is null when there is nothing readable to call it. */
    @Immutable
    data class NetworkUi(
        val kind: NetworkKindUi,
        val name: String?,
    )

    enum class NetworkKindUi { WiFi, Mobile, Wired, Other }

    @Immutable
    data class DeviceUi(
        val id: String,
        val name: String,
        val kind: DeviceKind?,
        val online: Boolean,
        val unreachable: Boolean = false,
        val lastSeenLabel: String? = null,
    )

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
        val Sample = DashboardState(
            storage = StorageUi(
                totalBytes = 128_000_000_000,
                freeBytes = 20_000_000_000,
                appBytes = 23_000_000_000,
            ),
            links = listOf(
                LinkUi(
                    id = "camera",
                    label = "Camera",
                    deviceName = "Server",
                    deviceKind = DeviceKind.Nas,
                    mode = SourceModeUi.AutoUpload,
                    outgoing = true,
                    status = LinkStatusUi.Syncing,
                    progress = 0.4f,
                ),
                LinkUi(
                    id = "documents",
                    label = "Documents",
                    deviceName = "Laptop",
                    deviceKind = DeviceKind.Laptop,
                    mode = SourceModeUi.Sync,
                    outgoing = true,
                    status = LinkStatusUi.Active,
                    statusDetail = "5 min. ago",
                ),
                LinkUi(
                    id = "whatsapp",
                    label = "WhatsApp Media",
                    deviceName = "Server",
                    deviceKind = DeviceKind.Nas,
                    mode = SourceModeUi.Offload,
                    outgoing = true,
                    status = LinkStatusUi.Disabled,
                    statusDetail = "declined by the peer",
                ),
                LinkUi(
                    id = "downloads",
                    label = "Downloads",
                    deviceName = "Home PC",
                    deviceKind = DeviceKind.Desktop,
                    mode = SourceModeUi.AutoUpload,
                    outgoing = false,
                    status = LinkStatusUi.Pending,
                ),
            ),
            network = NetworkUi(NetworkKindUi.WiFi, "Home_5G"),
            discovering = true,
            devices = listOf(
                DeviceUi("laptop", "Laptop", DeviceKind.Laptop, online = true),
                DeviceUi("server", "Server", DeviceKind.Nas, online = true),
                DeviceUi(
                    id = "home-pc",
                    name = "Home PC",
                    kind = DeviceKind.Desktop,
                    online = false,
                    lastSeenLabel = "yesterday",
                ),
            ),
            syncRequestsWaiting = 3,
        )
    }
}
