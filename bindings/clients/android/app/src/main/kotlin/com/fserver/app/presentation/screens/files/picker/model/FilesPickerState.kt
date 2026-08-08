package com.fserver.app.presentation.screens.files.picker.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.model.FileKindUi

/**
 * The picker is one screen with two stages: a source chooser, and — once a source has been
 * opened — that source's own browsing shape. [activeSource] is which stage is showing.
 *
 * Each source contributes to the same selection, so [selectedCount] sums all three and the
 * user can leave a source, open another and keep what was already picked.
 */
data class FilesPickerState(
    val sources: List<PickerSource> = PickerSource.entries.toList(),
    val activeSource: PickerSource? = null,
    val isLoading: Boolean = false,
    val entries: List<PickedEntryUi> = emptyList(),
    val tree: List<TreeNodeUi> = emptyList(),
    val mediaGrouping: MediaGrouping = MediaGrouping.Type,
    val mediaTabs: List<MediaTabUi> = emptyList(),
    val activeMediaTabId: String? = null,
    val selectedCount: Int = 0,
) {
    val isEmpty: Boolean get() = entries.isEmpty()

    /** Done commits the selection, so it stays inert while there is nothing to commit. */
    val canConfirm: Boolean get() = selectedCount > 0

    /** Falls back to the first tab so a freshly built tab set needs no explicit pick. */
    val activeMediaTab: MediaTabUi?
        get() = mediaTabs.firstOrNull { it.id == activeMediaTabId } ?: mediaTabs.firstOrNull()

    enum class PickerSource {
        StorageAccessFramework,
        MediaStore,
        FullAccess,
    }

    /** How the MediaStore results are split into tabs. */
    enum class MediaGrouping {
        Type,
        Directory,
    }

    @Immutable
    data class PickedEntryUi(
        val id: String,
        val name: String,
        val path: String,
        val kind: FileKindUi,
        val isDirectory: Boolean,
        val detailLabel: String? = null,
    )

    /**
     * One visible row of the full-access tree, already flattened: only expanded branches
     * contribute rows. Files are not rows at all — the tree selects directories.
     */
    @Immutable
    data class TreeNodeUi(
        val id: String,
        val name: String,
        val depth: Int,
        val expandable: Boolean,
        val expanded: Boolean,
        val selected: Boolean,
        val detailLabel: String? = null,
    )

    /**
     * One tab of the media browser. Type grouping sets [kind] and the UI names the tab from
     * it; folder grouping leaves it null and the bucket name in [title] is the label.
     */
    @Immutable
    data class MediaTabUi(
        val id: String,
        val title: String,
        val items: List<MediaItemUi>,
        val kind: FileKindUi? = null,
    ) {
        /** Images and videos render as a grid; everything else as rows under it. */
        val visualItems: List<MediaItemUi> get() = items.filter { it.isVisual }
        val otherItems: List<MediaItemUi> get() = items.filterNot { it.isVisual }
    }

    @Immutable
    data class MediaItemUi(
        val id: String,
        val name: String,
        val kind: FileKindUi,
        val selected: Boolean,
        val detailLabel: String? = null,
        val extensionLabel: String? = null,
    ) {
        val isVisual: Boolean get() = kind == FileKindUi.Image || kind == FileKindUi.Video
    }

    companion object {
        val SampleEntries = listOf(
            PickedEntryUi(
                id = "p-shoot",
                name = "Shoot",
                path = "/storage/emulated/0/DCIM/Shoot",
                kind = FileKindUi.Folder,
                isDirectory = true,
                detailLabel = "14 files · 2.1 GB",
            ),
            PickedEntryUi(
                id = "p-img4831",
                name = "IMG_4831.RAW",
                path = "/storage/emulated/0/DCIM/IMG_4831.RAW",
                kind = FileKindUi.Image,
                isDirectory = false,
                detailLabel = "28.4 MB",
            ),
            PickedEntryUi(
                id = "p-interview",
                name = "interview_02.wav",
                path = "/storage/emulated/0/Recordings/interview_02.wav",
                kind = FileKindUi.Audio,
                isDirectory = false,
                detailLabel = "112 MB",
            ),
            PickedEntryUi(
                id = "p-estimate",
                name = "estimate_final.pdf",
                path = "/storage/emulated/0/Documents/estimate_final.pdf",
                kind = FileKindUi.Document,
                isDirectory = false,
                detailLabel = "1.2 MB",
            ),
        )

        val SampleTree = listOf(
            TreeNodeUi(
                id = "/storage/emulated/0",
                name = "Internal storage",
                depth = 0,
                expandable = true,
                expanded = true,
                selected = false,
                detailLabel = "18 items",
            ),
            TreeNodeUi(
                id = "/storage/emulated/0/DCIM",
                name = "DCIM",
                depth = 1,
                expandable = true,
                expanded = true,
                selected = true,
                detailLabel = "4 items",
            ),
            TreeNodeUi(
                id = "/storage/emulated/0/DCIM/Camera",
                name = "Camera",
                depth = 2,
                expandable = false,
                expanded = false,
                selected = false,
                detailLabel = "312 items",
            ),
            TreeNodeUi(
                id = "/storage/emulated/0/Documents",
                name = "Documents",
                depth = 1,
                expandable = true,
                expanded = false,
                selected = false,
                detailLabel = "27 items",
            ),
        )

        val SampleMediaTabs = listOf(
            MediaTabUi(
                id = "kind-Image",
                kind = FileKindUi.Image,
                title = "Images",
                items = listOf(
                    MediaItemUi(
                        id = "m-1",
                        name = "IMG_4831.jpg",
                        kind = FileKindUi.Image,
                        selected = true,
                        detailLabel = "4.1 MB",
                    ),
                    MediaItemUi(
                        id = "m-2",
                        name = "IMG_4832.jpg",
                        kind = FileKindUi.Image,
                        selected = false,
                        detailLabel = "3.8 MB",
                    ),
                ),
            ),
            MediaTabUi(
                id = "kind-Audio",
                kind = FileKindUi.Audio,
                title = "Audio",
                items = listOf(
                    MediaItemUi(
                        id = "m-3",
                        name = "interview_02.wav",
                        kind = FileKindUi.Audio,
                        selected = false,
                        detailLabel = "112 MB",
                        extensionLabel = "WAV",
                    ),
                ),
            ),
        )
    }
}
