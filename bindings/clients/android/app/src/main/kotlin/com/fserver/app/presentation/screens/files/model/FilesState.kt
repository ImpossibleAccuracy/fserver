package com.fserver.app.presentation.screens.files.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.shared.browser.model.FileSortUi
import com.fserver.app.presentation.shared.browser.model.SampleFiles
import com.fserver.common.model.FileSize
import com.fserver.core.network.device.model.DeviceKind

@Immutable
data class FilesState(
    /** Devices with at least one folder, for the filter sheet. */
    val devices: List<DeviceUi> = emptyList(),
    val selectedDeviceId: String? = null,
    val filter: FilterUi = FilterUi.All,
    val entries: FeedUi? = null,
    val openedPath: String? = null,
    val sort: FileSortUi = FileSortUi.Name,
    val sortAscending: Boolean = true,
    val isSyncing: Boolean = false,
) {
    val selectedDevice: DeviceUi?
        get() = devices.firstOrNull { it.id == selectedDeviceId }

    val isFiltered: Boolean
        get() = filter != FilterUi.All || selectedDevice != null

    /** Directories from the top of the tree to the opened folder; empty at the top. */
    val openedTrail: List<FileBrowserUi.Directory>
        get() = openedPath?.let { entries?.preview?.trailTo(it) }.orEmpty()

    val openedDirectory: FileBrowserUi.Directory?
        get() = openedTrail.lastOrNull()

    val showsCloudNotice: Boolean
        get() = openedDirectory?.contents?.any { it is FileBrowserUi.File && it.isRemoteOnly } == true

    val emptyReason: EmptyReasonUi
        get() = when {
            entries?.deviceId != null -> EmptyReasonUi.NoDeviceFiles
            entries?.filter == FilterUi.Local -> EmptyReasonUi.NoLocalFiles
            entries?.filter == FilterUi.Cloud -> EmptyReasonUi.NoCloudFiles
            else -> EmptyReasonUi.NoFiles
        }

    @Immutable
    data class FeedUi(
        val preview: FileBrowserUi.Tree,
        val filter: FilterUi,
        val deviceId: String?,
        val sort: FileSortUi = FileSortUi.Name,
        val sortAscending: Boolean = true,
    )

    @Immutable
    data class DeviceUi(
        val id: String,
        val name: String,
        val kind: DeviceKind?,
    )

    enum class FilterUi { All, Local, Cloud }

    enum class EmptyReasonUi { NoFiles, NoLocalFiles, NoCloudFiles, NoDeviceFiles }

    companion object {
        val SampleEntries = FeedUi(
            preview = FileBrowserUi.Tree(
                directories = listOf(
                    FileBrowserUi.Directory(
                        path = "/DCIM",
                        name = "DCIM",
                        files = FileBrowserUi.SampleFiles.size,
                        size = FileSize(1_960_000_000),
                        contents = FileBrowserUi.SampleFiles.drop(1),
                    ),
                ),
            ),
            filter = FilterUi.All,
            deviceId = null,
        )

        val SampleDevices = listOf(
            DeviceUi("laptop", "Laptop", DeviceKind.Laptop),
            DeviceUi("server", "Server", DeviceKind.Nas),
        )
    }
}
