package com.fserver.app.presentation.screens.files.model

import com.fserver.app.presentation.shared.browser.model.FileSortUi

sealed interface FilesIntent {
    /** Both filters at once, as the sheet closes. A null device clears that filter. */
    data class FiltersApplied(
        val deviceId: String?,
        val filter: FilesState.FilterUi,
    ) : FilesIntent
    data class EntryClicked(val entryId: String) : FilesIntent
    data object RefreshRequested : FilesIntent

    data class FolderOpened(val path: String) : FilesIntent
    data object FolderUp : FilesIntent

    /** Back to the top of the tree, from a breadcrumb. */
    data object FolderClosed : FilesIntent
    data class SortSelected(val sort: FileSortUi) : FilesIntent
}
