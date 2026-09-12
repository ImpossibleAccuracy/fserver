package com.fserver.app.presentation.screens.source.list.model

sealed interface SyncRequestListUiEffect {
    data class ShowMessage(val message: String) : SyncRequestListUiEffect
}
