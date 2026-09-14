package com.fserver.app.presentation.navigation.model

sealed interface AppRootUiEffect {
    data object SyncFailed : AppRootUiEffect
}
