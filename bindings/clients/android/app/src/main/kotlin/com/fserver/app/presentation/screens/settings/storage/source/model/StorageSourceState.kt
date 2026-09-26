package com.fserver.app.presentation.screens.settings.storage.source.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.app.presentation.composable.model.LinkDirectionUi
import com.fserver.app.presentation.screens.settings.storage.main.model.PeerUi
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.common.model.FileSize
import com.fserver.core.network.device.model.DeviceKind

@Immutable
data class StorageSourceState(
    val isLoading: Boolean = true,
    val exists: Boolean = true,
    val label: String = "",
    val path: String? = null,
    val peer: PeerUi = PeerUi(),
    val direction: LinkDirectionUi = LinkDirectionUi.Outgoing,
    val offPhoneFiles: Int = 0,
    val sort: SortUi = SortUi.Size,
    val grouped: Boolean = false,
    val groups: List<GroupUi> = emptyList(),
    val editing: Boolean = false,
    val selected: Set<String> = emptySet(),
) {
    val files: List<FileUi>
        get() = groups.flatMap { it.files }

    val totalBytes: Long
        get() = groups.sumOf { it.bytes }

    val isEmpty: Boolean
        get() = !isLoading && exists && groups.all { it.files.isEmpty() }

    val selectedBytes: Long
        get() = files.filter { it.id in selected }.sumOf { it.bytes }

    val selectedWithoutCopy: Int
        get() = files.count { it.id in selected && it.status != CopyStatusUi.OnPeer }

    val allSelected: Boolean
        get() = selected.isNotEmpty() && selected.size == files.size

    @Immutable
    data class GroupUi(
        val folder: String?,
        val files: List<FileUi>,
    ) {
        val bytes: Long
            get() = files.sumOf { it.bytes }
    }

    @Immutable
    data class FileUi(
        val id: String,
        val folder: String?,
        val status: CopyStatusUi,
        val preview: FileBrowserUi.File,
    ) {
        val name: String
            get() = preview.name

        val bytes: Long
            get() = preview.size?.bytes ?: 0
    }

    enum class CopyStatusUi { OnPeer, Waiting, Sending }

    enum class SortUi { Size, Date, Name }

    companion object {
        private fun sampleFile(
            name: String,
            folder: String,
            bytes: Long,
            status: CopyStatusUi = CopyStatusUi.OnPeer,
        ) = FileUi(
            id = name,
            folder = folder,
            status = status,
            preview = FileBrowserUi.File(
                id = name,
                path = "$folder/$name",
                name = name,
                kind = if (name.endsWith(".mp4")) FileKindUi.Video else FileKindUi.Image,
                locator = null,
                size = FileSize(bytes),
                extensionLabel = null,
            ),
        )

        val Sample = StorageSourceState(
            isLoading = false,
            label = "Camera",
            path = "/DCIM",
            peer = PeerUi("Server", DeviceKind.Nas),
            groups = listOf(
                GroupUi(
                    folder = null,
                    files = listOf(
                        sampleFile("VID_20260814_1902.mp4", "Camera", 4_100_000_000),
                        sampleFile("VID_20260726_2010.mp4", "Video", 2_900_000_000),
                        sampleFile("VID_20260902_1144.mp4", "Camera", 1_600_000_000, CopyStatusUi.Waiting),
                        sampleFile("VID_20250611_1733.mp4", "Camera", 1_200_000_000),
                        sampleFile("IMG_4410.dng", "Camera", 800_000_000),
                        sampleFile("VID_20260920_0915.mp4", "Camera", 700_000_000, CopyStatusUi.Sending),
                    ),
                ),
            ),
        )

        val SampleEditing = Sample.copy(
            editing = true,
            selected = setOf("VID_20260814_1902.mp4", "VID_20260902_1144.mp4"),
        )

        val SampleOffPhone = StorageSourceState(
            isLoading = false,
            label = "WhatsApp Media",
            peer = PeerUi("Server", DeviceKind.Nas),
            offPhoneFiles = 7860,
        )
    }
}
