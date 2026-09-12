package com.fserver.app.presentation.screens.files.list.model

sealed interface FilesIntent {
    data class DeviceClicked(val deviceId: String) : FilesIntent
    data class DeviceExpanded(val deviceId: String) : FilesIntent
    data object DeviceCollapsed : FilesIntent
    data class FilterSelected(val filter: FilesState.FilterUi) : FilesIntent
    data object FilterCleared : FilesIntent
    data class EntryClicked(val entryId: String) : FilesIntent
    data object SearchClicked : FilesIntent
    data object SyncRequestHintDismissed : FilesIntent
}
