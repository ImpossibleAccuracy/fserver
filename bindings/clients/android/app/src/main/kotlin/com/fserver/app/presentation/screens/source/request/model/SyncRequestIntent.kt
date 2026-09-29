package com.fserver.app.presentation.screens.source.request.model

import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesIntent

sealed interface SyncRequestIntent {
    data object Declined : SyncRequestIntent

    data object AppStorageSelected : SyncRequestIntent

    data class FolderPicked(
        val uri: String,
        val label: String,
        val hasFiles: Boolean,
    ) : SyncRequestIntent

    data object FolderSelected : SyncRequestIntent

    data class DeviceAccessAnswered(val granted: Boolean) : SyncRequestIntent

    data object DirectorySelected : SyncRequestIntent

    data object DirectoryConfirmed : SyncRequestIntent

    data object DirectoryPickCancelled : SyncRequestIntent

    data class PreferencesChanged(val intent: SourcePreferencesIntent) : SyncRequestIntent

    data object Accepted : SyncRequestIntent
}
