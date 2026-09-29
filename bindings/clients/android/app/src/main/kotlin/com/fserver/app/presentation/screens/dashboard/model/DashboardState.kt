package com.fserver.app.presentation.screens.dashboard.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.composable.model.LinkDirectionUi
import com.fserver.app.presentation.composable.model.PeerUi
import com.fserver.app.presentation.composable.model.StorageUsageUi
import com.fserver.app.presentation.model.UiText
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.core.network.device.model.DeviceKind

@Immutable
data class DashboardState(
    val isLoading: Boolean = true,
    val storage: StorageUsageUi? = null,
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
        get() = devices.count { it.peer.online }

    /** One source, read as "what goes where". */
    @Immutable
    data class LinkUi(
        val id: String,
        val label: String,
        val peer: PeerUi,
        val mode: SourceModeUi,
        val direction: LinkDirectionUi,
        val status: LinkStatusUi,
        /** When it last synced, or why it is disabled. */
        val statusDetail: String? = null,
        val progress: Float? = null,
        val filesDone: Int = 0,
        val filesTotal: Int = 0,
        val filesSkipped: Int = 0,
        /** Why the device cannot be reached, or why the last pass failed. */
        val error: UiText? = null,
    )

    enum class LinkStatusUi { Pending, Active, Syncing, Disabled }

    /** The link this phone is on. [name] is null when there is nothing readable to call it. */
    @Immutable
    data class NetworkUi(
        val kind: NetworkKindUi,
        val name: String?,
    )

    enum class NetworkKindUi { WiFi, Mobile, Wired, Other }

    /** [unreachable]: offline, and the last attempts to reach it failed. */
    @Immutable
    data class DeviceUi(
        val peer: PeerUi,
        val unreachable: Boolean = false,
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
        private val SampleLaptop = PeerUi("laptop", "Laptop", DeviceKind.Laptop, online = true)
        private val SampleServer = PeerUi("server", "Server", DeviceKind.Nas, online = true)
        private val SamplePc = PeerUi("home-pc", "Home PC", DeviceKind.Desktop)

        val Sample = DashboardState(
            isLoading = false,
            storage = StorageUsageUi.Sample,
            links = listOf(
                LinkUi(
                    id = "camera",
                    label = "Camera",
                    peer = SampleServer,
                    mode = SourceModeUi.AutoUpload,
                    direction = LinkDirectionUi.Outgoing,
                    status = LinkStatusUi.Syncing,
                    progress = 0.4f,
                    filesDone = 12,
                    filesTotal = 30,
                ),
                LinkUi(
                    id = "documents",
                    label = "Documents",
                    peer = SampleLaptop,
                    mode = SourceModeUi.Sync,
                    direction = LinkDirectionUi.Mirror,
                    status = LinkStatusUi.Active,
                    statusDetail = "5 min. ago",
                    filesSkipped = 3,
                ),
                LinkUi(
                    id = "whatsapp",
                    label = "WhatsApp Media",
                    peer = SampleServer,
                    mode = SourceModeUi.Offload,
                    direction = LinkDirectionUi.Outgoing,
                    status = LinkStatusUi.Disabled,
                    statusDetail = "declined by the peer",
                ),
                LinkUi(
                    id = "downloads",
                    label = "Downloads",
                    peer = SamplePc,
                    mode = SourceModeUi.AutoUpload,
                    direction = LinkDirectionUi.Incoming,
                    status = LinkStatusUi.Active,
                    error = UiText.Text("device is not answering"),
                ),
            ),
            network = NetworkUi(NetworkKindUi.WiFi, "Home_5G"),
            discovering = true,
            devices = listOf(
                DeviceUi(SampleLaptop),
                DeviceUi(SampleServer),
                DeviceUi(SamplePc),
            ),
            syncRequestsWaiting = 3,
        )
    }
}
