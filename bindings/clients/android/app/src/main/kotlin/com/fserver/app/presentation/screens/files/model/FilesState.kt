package com.fserver.app.presentation.screens.files.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.shared.browser.model.FileSortUi
import com.fserver.app.presentation.shared.browser.model.SampleFiles
import com.fserver.common.model.FileSize
import com.fserver.core.network.device.model.DeviceKind
import kotlinx.serialization.Serializable

@Immutable
data class FilesState(
    val sources: List<SourceUi> = emptyList(),
    val selectedSourceId: String? = null,
    val filter: FilterUi = FilterUi.All,
    val entries: FeedUi? = null,
    val openedPath: String? = null,
    val sort: FileSortUi = FileSortUi.Name,
    val sortAscending: Boolean = true,
    val isSyncing: Boolean = false,
) {
    val selectedSource: SourceUi?
        get() = sources.firstOrNull { it.id == selectedSourceId }

    val isFiltered: Boolean
        get() = filter != FilterUi.All || selectedSource != null

    /** Directories from the top of the tree to the opened folder; empty at the top. */
    val openedTrail: List<FileBrowserUi.Directory>
        get() = openedPath?.let { entries?.preview?.trailTo(it) }.orEmpty()

    val openedDirectory: FileBrowserUi.Directory?
        get() = openedTrail.lastOrNull()

    val showsCloudNotice: Boolean
        get() = openedDirectory?.contents?.any { it is FileBrowserUi.File && it.isRemoteOnly } == true

    val emptyReason: EmptyReasonUi
        get() = when {
            entries?.sourceId != null -> EmptyReasonUi.NoSourceFiles
            entries?.filter == FilterUi.Local -> EmptyReasonUi.NoLocalFiles
            entries?.filter == FilterUi.Cloud -> EmptyReasonUi.NoCloudFiles
            else -> EmptyReasonUi.NoFiles
        }

    @Immutable
    data class FeedUi(
        val preview: FileBrowserUi.Tree,
        val filter: FilterUi,
        val sourceId: String?,
        val sort: FileSortUi = FileSortUi.Name,
        val sortAscending: Boolean = true,
    )

    @Immutable
    data class SourceUi(
        val id: String,
        val label: String,
        val deviceName: String,
        val deviceKind: DeviceKind?,
    )

    @Serializable
    enum class FilterUi { All, Local, Cloud }

    enum class EmptyReasonUi { NoFiles, NoLocalFiles, NoCloudFiles, NoSourceFiles }

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
            sourceId = null,
        )

        val SampleSources = listOf(
            SourceUi("camera", "Camera", "Server", DeviceKind.Nas),
            SourceUi("documents", "Documents", "Laptop", DeviceKind.Laptop),
        )
    }
}
