package com.fserver.app.presentation.screens.files.picker.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.model.FileKindUi

data class FilesPickerState(
    val sources: List<PickerSource> = PickerSource.entries.toList(),
    val entries: List<PickedEntryUi> = emptyList(),
) {
    val isEmpty: Boolean get() = entries.isEmpty()

    enum class PickerSource {
        StorageAccessFramework,
        MediaStore,
        FullAccess,
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
    }
}
