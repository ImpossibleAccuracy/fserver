package com.fserver.app.presentation.screens.files.model

import com.fserver.app.presentation.shared.browser.model.FileKey
import com.fserver.app.presentation.composable.model.PeerUi
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
    val editing: Boolean = false,
    val selected: Set<FileKey> = emptySet(),
    val selectedFolders: Set<String> = emptySet(),
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

    val selectedCount: Int
        get() = selected.size + selectedFolders.size

    val selectionActions: Set<FileActionUi>
        get() {
            val perEntry = selected.map(::actionsFor) + selectedFolders.map(::folderActionsFor)
            if (perEntry.isEmpty()) return emptySet()
            val common = perEntry.reduce { acc, actions -> acc intersect actions } - PinActions
            val single = if (perEntry.size == 1) common else common - FileActionUi.Edit - FileActionUi.Rename
            val pin = if (perEntry.all { it.any(PinActions::contains) }) pinActionOf(perEntry) else null
            return single + listOfNotNull(pin)
        }

    val selectionPinTargets: Set<FileKey>
        get() = (selected + selectedFolders.flatMap(::folderFiles))
            .filterTo(mutableSetOf()) { key -> actionsFor(key).any(PinActions::contains) }

    fun actionsFor(file: FileKey): Set<FileActionUi> = entries?.actions?.get(file).orEmpty()

    fun folderActionsFor(path: String): Set<FileActionUi> {
        val files = folderFiles(path).map(::actionsFor)
        if (files.isEmpty()) return emptySet()

        return buildSet {
            if (files.all { FileActionUi.Delete in it }) {
                add(FileActionUi.Rename)
                add(FileActionUi.Delete)
            }
            pinActionOf(files)?.let(::add)
        }
    }

    private fun folderFiles(path: String): List<FileKey> =
        entries?.preview?.trailTo(path)?.lastOrNull()?.contents?.fileKeys().orEmpty()

    fun file(key: FileKey): FileBrowserUi.File? = entries?.preview?.directories?.findFile(key)

    val emptyReason: EmptyReasonUi
        get() = when {
            entries?.sourceId != null -> EmptyReasonUi.NoSourceFiles
            entries?.filter == FilterUi.Local -> EmptyReasonUi.NoLocalFiles
            entries?.filter == FilterUi.Cloud -> EmptyReasonUi.NoCloudFiles
            entries?.filter == FilterUi.Pinned -> EmptyReasonUi.NoPinnedFiles
            else -> EmptyReasonUi.NoFiles
        }

    @Immutable
    data class FeedUi(
        val preview: FileBrowserUi.Tree,
        val filter: FilterUi,
        val sourceId: String?,
        val sort: FileSortUi = FileSortUi.Name,
        val sortAscending: Boolean = true,
        val actions: Map<FileKey, Set<FileActionUi>> = emptyMap(),
    )

    enum class FileActionUi { Edit, Rename, Pin, Unpin, Delete }

    @Immutable
    data class SourceUi(
        val id: String,
        val label: String,
        val peer: PeerUi,
    )

    @Serializable
    enum class FilterUi { All, Local, Cloud, Pinned }

    enum class EmptyReasonUi { NoFiles, NoLocalFiles, NoCloudFiles, NoPinnedFiles, NoSourceFiles }

    companion object {
        private val PinActions = setOf(FileActionUi.Pin, FileActionUi.Unpin)

        private fun pinActionOf(actions: List<Set<FileActionUi>>): FileActionUi? = when {
            actions.any { FileActionUi.Pin in it } -> FileActionUi.Pin
            actions.any { FileActionUi.Unpin in it } -> FileActionUi.Unpin
            else -> null
        }

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
            SourceUi("camera", "Camera", PeerUi("server", "Server", DeviceKind.Nas)),
            SourceUi("documents", "Documents", PeerUi("laptop", "Laptop", DeviceKind.Laptop)),
        )
    }
}

private fun List<FileBrowserUi.PreviewContentEntry>.findFile(key: FileKey): FileBrowserUi.File? {
    for (entry in this) {
        when (entry) {
            is FileBrowserUi.File -> if (entry.key == key) return entry
            is FileBrowserUi.Directory -> entry.contents.findFile(key)?.let { return it }
        }
    }
    return null
}

private fun List<FileBrowserUi.PreviewContentEntry>.fileKeys(): List<FileKey> = flatMap { entry ->
    when (entry) {
        is FileBrowserUi.File -> listOf(entry.indexedKey)
        is FileBrowserUi.Directory -> entry.contents.fileKeys()
    }
}
