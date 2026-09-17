package com.fserver.app.presentation.screens.files.folder.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.screens.source.shared.preview.model.SourcePreviewUi

@Immutable
data class FolderState(
    val title: String = "",
    val summary: String? = null,
    val entries: SourcePreviewUi? = null,
    val showsCloudNotice: Boolean = false,
    val sort: SortUi = SortUi.Name,
    val sortAscending: Boolean = true,
) {
    val isMediaCollection: Boolean
        get() = entries is SourcePreviewUi.Gallery

    val showsSort: Boolean
        get() = entries?.isEmpty == false

    enum class SortUi { Name, Date, Size, Kind }
}
