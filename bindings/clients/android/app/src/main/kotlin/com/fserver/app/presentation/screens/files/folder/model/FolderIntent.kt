package com.fserver.app.presentation.screens.files.folder.model

sealed interface FolderIntent {
    data object ViewToggled : FolderIntent
    data class ItemClicked(val itemId: String) : FolderIntent
}
