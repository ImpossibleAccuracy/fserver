package com.fserver.app.presentation.screens.source.list.model

sealed interface SyncRequestListUiEffect {
    data object Close : SyncRequestListUiEffect
}
