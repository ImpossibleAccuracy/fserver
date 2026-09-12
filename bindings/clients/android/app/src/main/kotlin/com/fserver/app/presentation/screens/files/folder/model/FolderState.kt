package com.fserver.app.presentation.screens.files.folder.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.composable.model.FileAvailabilityUi
import com.fserver.app.presentation.composable.model.FileKindUi
import com.fserver.app.presentation.composable.model.FileUi

@Immutable
data class FolderState(
    val title: String = "",
    val summary: String = "",
    val mediaCollection: Boolean = true,
    val items: List<ItemUi> = emptyList(),
    val showsCloudNotice: Boolean = false,
) {
    @Immutable
    data class ItemUi(
        val file: FileUi,
        val pinned: Boolean = false,
    ) {
        val id: String get() = file.id
    }

    companion object {
        private val Availabilities = listOf(
            FileAvailabilityUi.OnDevice,
            FileAvailabilityUi.Offloaded,
            FileAvailabilityUi.OnDevice,
            FileAvailabilityUi.Offloaded,
            FileAvailabilityUi.OnDevice,
            FileAvailabilityUi.Offloaded,
            FileAvailabilityUi.OnDevice,
            FileAvailabilityUi.OnDevice,
            FileAvailabilityUi.Offloaded,
            FileAvailabilityUi.OnServer,
            FileAvailabilityUi.OnDevice,
            FileAvailabilityUi.OnDevice,
        )

        val SampleItems: List<ItemUi> = Availabilities.mapIndexed { index, availability ->
            ItemUi(
                file = FileUi(
                    id = "tile-$index",
                    name = "IMG_${4800 + index}.heic",
                    kind = if (index == 2 || index == 11) FileKindUi.Video else FileKindUi.Image,
                    durationLabel = "0:42".takeIf { index == 2 || index == 11 },
                    availability = availability,
                ),
                pinned = index == 7,
            )
        }
    }
}
