package com.fserver.app.presentation.screens.source.request.location.model

sealed interface SyncRequestLocationIntent {
    data object AppStorageSelected : SyncRequestLocationIntent

    data class FolderPicked(val uri: String, val label: String) : SyncRequestLocationIntent
}
