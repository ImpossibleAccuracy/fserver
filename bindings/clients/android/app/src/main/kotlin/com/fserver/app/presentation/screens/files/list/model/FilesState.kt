package com.fserver.app.presentation.screens.files.list.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.screens.source.request.shared.model.SyncRequestUi
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.app.presentation.screens.source.shared.preview.model.SourcePreviewUi
import com.fserver.core.network.device.model.DeviceKind

@Immutable
data class FilesState(
    val devices: List<DeviceUi> = emptyList(),
    val selectedDeviceId: String? = null,
    val filter: FilterUi = FilterUi.All,
    val entries: SourcePreviewUi? = null,
    val expandedDevice: DeviceDetailsUi? = null,
    val syncRequest: SyncRequestUi? = null,
    val syncRequestsWaiting: Int = 0,
    val syncRequestHintDismissed: Boolean = false,
) {
    val selectedDevice: DeviceUi?
        get() = devices.firstOrNull { it.id == selectedDeviceId }

    val showsSyncRequestHint: Boolean
        get() = syncRequest != null && !syncRequestHintDismissed && selectedDevice == null

    @Immutable
    data class DeviceUi(
        val id: String,
        val name: String,
        val kind: DeviceKind?,
        val online: Boolean,
        val itemCount: Int,
    )

    @Immutable
    data class DeviceDetailsUi(
        val id: String,
        val name: String,
        val kind: DeviceKind?,
        val online: Boolean,
        val addressLabel: String?,
        val fingerprintLabel: String,
        val folders: List<FolderUi>,
    )

    @Immutable
    data class FolderUi(
        val id: String,
        val name: String,
        val mode: SourceModeUi,
        val detail: String,
        val accented: Boolean = false,
    )

    enum class FilterUi { All, Local, Cloud }

    companion object {
        val SampleDevices = listOf(
            DeviceUi("laptop", "Laptop", DeviceKind.Laptop, online = true, itemCount = 912),
            DeviceUi("server", "Server", DeviceKind.Nas, online = true, itemCount = 312),
            DeviceUi("home-pc", "Home PC", DeviceKind.Desktop, online = false, itemCount = 74),
        )

        val SampleFolders = listOf(
            FolderUi("camera", "Camera", SourceModeUi.Offload, "grid", accented = true),
            FolderUi("documents", "Documents", SourceModeUi.Sync, "list"),
            FolderUi("movies", "Movies", SourceModeUi.Host, "read only"),
        )

        fun sampleDetailsOf(device: DeviceUi): DeviceDetailsUi = DeviceDetailsUi(
            id = device.id,
            name = device.name,
            kind = device.kind,
            online = device.online,
            addressLabel = "192.168.1.40".takeIf { device.online },
            fingerprintLabel = "9f:2a:c1…",
            folders = SampleFolders,
        )
    }
}
