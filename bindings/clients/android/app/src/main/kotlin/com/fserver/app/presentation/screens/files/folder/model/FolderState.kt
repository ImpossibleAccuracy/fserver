package com.fserver.app.presentation.screens.files.folder.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi

@Immutable
data class FolderState(
    val title: String = "",
    val summary: String? = null,
    val entries: FileBrowserUi? = null,
    val showsCloudNotice: Boolean = false,
    val sort: SortUi = SortUi.Name,
    val sortAscending: Boolean = true,
) {
    val isMediaCollection: Boolean
        get() = entries is FileBrowserUi.Gallery

    val showsSort: Boolean
        get() = entries?.isEmpty == false

    enum class SortUi { Name, Date, Size, Kind }
}
