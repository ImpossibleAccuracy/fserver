package com.fserver.app.presentation.screens.settings.storage.source.model

import com.fserver.app.presentation.model.UiText
import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.app.presentation.composable.model.LinkDirectionUi
import com.fserver.app.presentation.composable.model.PeerUi
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.shared.browser.model.isMediaCollection
import com.fserver.common.model.FileSize
import com.fserver.core.network.device.model.DeviceKind

@Immutable
data class StorageSourceState(
    val isLoading: Boolean = true,
    val exists: Boolean = true,
    val label: String = "",
    val path: UiText? = null,
    val peer: PeerUi = PeerUi(),
    val direction: LinkDirectionUi = LinkDirectionUi.Outgoing,
    val offPhoneFiles: Int = 0,
    val sort: SortUi = SortUi.Size,
    val grouped: Boolean = false,
    val hasFolders: Boolean = false,
    val files: List<FileBrowserUi.File> = emptyList(),
    val tree: FileBrowserUi.Tree = FileBrowserUi.Tree(),
    val editing: Boolean = false,
    val selected: Set<String> = emptySet(),
    val freeBlock: FreeBlockUi? = null,
    val refusals: Map<String, RefusalUi> = emptyMap(),
) {
    val preview: FileBrowserUi = when {
        grouped -> tree
        isMediaCollection(files) -> FileBrowserUi.Gallery(files)
        else -> FileBrowserUi.PlainList(files)
    }

    val totalBytes: Long
        get() = files.sumOf { it.bytes }

    val isEmpty: Boolean
        get() = !isLoading && exists && files.isEmpty()

    val selectedBytes: Long
        get() = files.filter { it.id in selected }.sumOf { it.bytes }

    val canFree: Boolean
        get() = freeBlock == null

    val freeable: Set<String>
        get() = selected - refusals.keys

    val freeableBytes: Long
        get() = files.filter { it.id in freeable }.sumOf { it.bytes }

    val selectedRefusals: Map<RefusalUi, Int>
        get() = selected.mapNotNull { refusals[it] }.groupingBy { it }.eachCount()

    val allSelected: Boolean
        get() = selected.isNotEmpty() && selected.size == files.size

    enum class SortUi { Size, Date, Name }

    enum class FreeBlockUi { Keeper, Inactive }

    enum class RefusalUi { NotOnPeer, PeerDiffers, Unverified, Pinned }

    companion object {
        private fun sampleFile(
            name: String,
            bytes: Long,
            sync: FileBrowserUi.File.Sync? = null,
        ) = FileBrowserUi.File(
            id = name,
            path = "Camera/$name",
            name = name,
            kind = if (name.endsWith(".mp4")) FileKindUi.Video else FileKindUi.Image,
            locator = null,
            size = FileSize(bytes),
            locations = buildSet {
                add(FileBrowserUi.File.Location.Local)
                if (sync == null) add(FileBrowserUi.File.Location.Remote)
            },
            extensionLabel = null,
            sync = sync,
        )

        val Sample = StorageSourceState(
            isLoading = false,
            label = "Camera",
            path = UiText.Text("/DCIM"),
            peer = PeerUi("server", "Server", DeviceKind.Nas),
            hasFolders = true,
            files = listOf(
                sampleFile("VID_20260814_1902.mp4", 4_100_000_000),
                sampleFile("VID_20260726_2010.mp4", 2_900_000_000),
                sampleFile("VID_20260902_1144.mp4", 1_600_000_000, FileBrowserUi.File.Sync.Waiting),
                sampleFile("VID_20250611_1733.mp4", 1_200_000_000),
                sampleFile("IMG_4410.dng", 800_000_000),
                sampleFile("VID_20260920_0915.mp4", 700_000_000, FileBrowserUi.File.Sync.Sending(0.4f)),
            ),
        )

        val SampleEditing = Sample.copy(
            editing = true,
            selected = setOf("VID_20260814_1902.mp4", "VID_20260902_1144.mp4"),
            refusals = mapOf(
                "VID_20260902_1144.mp4" to RefusalUi.NotOnPeer,
                "VID_20260920_0915.mp4" to RefusalUi.NotOnPeer,
            ),
        )

        val SampleKeeper = Sample.copy(freeBlock = FreeBlockUi.Keeper)

        val SampleOffPhone = StorageSourceState(
            isLoading = false,
            label = "WhatsApp Media",
            peer = PeerUi("server", "Server", DeviceKind.Nas),
            offPhoneFiles = 7860,
        )
    }
}

private val FileBrowserUi.File.bytes: Long
    get() = size?.bytes ?: 0
