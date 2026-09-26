package com.fserver.app.presentation.screens.settings.storage.main.model

sealed interface StorageIntent {
    data class FreeUpConfirmed(val keys: Set<String>) : StorageIntent
}
