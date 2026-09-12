package com.fserver.app.presentation.screens.files.list.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.composable.model.FileAvailabilityUi
import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.app.presentation.composable.model.FileUi
import com.fserver.app.presentation.screens.source.request.shared.model.SyncRequestUi
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
import com.fserver.core.network.device.model.DeviceKind

@Immutable
data class FilesState(
    val devices: List<DeviceUi> = emptyList(),
    val selectedDeviceId: String? = null,
    val filter: FilterUi = FilterUi.All,
    val entries: List<EntryUi> = emptyList(),
    val expandedDevice: DeviceDetailsUi? = null,
    val syncRequest: SyncRequestUi? = null,
    val syncRequestsWaiting: Int = 0,
    val syncRequestHintDismissed: Boolean = false,
) {
    val selectedDevice: DeviceUi?
        get() = devices.firstOrNull { it.id == selectedDeviceId }

    val showsSyncRequestHint: Boolean
        get() = syncRequest != null && !syncRequestHintDismissed && selectedDevice == null

    val isEmpty: Boolean
        get() = devices.isEmpty() && entries.isEmpty()

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

    @Immutable
    data class EntryUi(
        val file: FileUi,
        val isFolder: Boolean = false,
        val childLabel: String? = null,
        val conflicted: Boolean = false,
        val mediaCollection: Boolean = false,
    ) {
        val id: String get() = file.id
    }

    enum class FilterUi { All, Local, Cloud }

    companion object {
        val SampleDevices = listOf(
            DeviceUi("laptop", "Laptop", DeviceKind.Laptop, online = true, itemCount = 912),
            DeviceUi("server", "Server", DeviceKind.Nas, online = true, itemCount = 312),
            DeviceUi("home-pc", "Home PC", DeviceKind.Desktop, online = false, itemCount = 74),
        )

        val SampleEntries = listOf(
            EntryUi(
                file = FileUi(id = "camera", name = "Camera", kind = FileKindUi.Image),
                isFolder = true,
                childLabel = "1 240 photos",
                mediaCollection = true,
            ),
            EntryUi(
                file = FileUi(
                    id = "contract",
                    name = "Contract.pdf",
                    kind = FileKindUi.Document,
                    sizeLabel = "2.1 MB",
                    dateLabel = "yesterday",
                    availability = FileAvailabilityUi.Offloaded,
                ),
            ),
            EntryUi(
                file = FileUi(
                    id = "archive",
                    name = "archive.zip",
                    kind = FileKindUi.Other,
                    sizeLabel = "840 MB",
                    dateLabel = "Server",
                    availability = FileAvailabilityUi.OnPeer,
                ),
            ),
            EntryUi(
                file = FileUi(
                    id = "notes",
                    name = "Notes.md",
                    kind = FileKindUi.Document,
                    sizeLabel = "12 KB",
                    dateLabel = "today",
                    availability = FileAvailabilityUi.OnDevice,
                ),
                conflicted = true,
            ),
            EntryUi(
                file = FileUi(
                    id = "clip",
                    name = "clip_final.mp4",
                    kind = FileKindUi.Video,
                    sizeLabel = "1.8 GB",
                    dateLabel = "Jul 24",
                    availability = FileAvailabilityUi.OnServer,
                ),
            ),
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
