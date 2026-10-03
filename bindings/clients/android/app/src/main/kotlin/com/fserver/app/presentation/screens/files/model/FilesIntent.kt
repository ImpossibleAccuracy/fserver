package com.fserver.app.presentation.screens.files.model

import com.fserver.app.presentation.shared.browser.model.FileKey
import com.fserver.app.presentation.shared.browser.model.FileSortUi

sealed interface FilesIntent {
    /** Both filters at once, as the sheet closes. A null source clears that filter. */
    data class FiltersApplied(
        val sourceId: String?,
        val filter: FilesState.FilterUi,
    ) : FilesIntent
    data class EntryClicked(val file: FileKey) : FilesIntent
    data object RefreshRequested : FilesIntent

    data class FolderOpened(val path: String) : FilesIntent
    data object FolderUp : FilesIntent

    /** Back to the top of the tree, from a breadcrumb. */
    data object FolderClosed : FilesIntent
    data class SortSelected(val sort: FileSortUi) : FilesIntent

    data class EntryLongPressed(val file: FileKey) : FilesIntent
    data class EntryToggled(val file: FileKey) : FilesIntent
    data class FolderLongPressed(val path: String) : FilesIntent
    data class FolderToggled(val path: String) : FilesIntent
    data object EditClosed : FilesIntent

    data class RenameConfirmed(val file: FileKey, val newName: String) : FilesIntent
    data class DeleteConfirmed(val files: Set<FileKey>) : FilesIntent
    data class PinRequested(val files: Set<FileKey>, val pinned: Boolean) : FilesIntent
}
