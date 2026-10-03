package com.fserver.app.presentation.screens.settings.storage.main.model

import android.net.Uri

sealed interface StorageIntent {
    data class FreeUpConfirmed(val keys: Set<String>) : StorageIntent

    data class Export(val uri: Uri) : StorageIntent
}
