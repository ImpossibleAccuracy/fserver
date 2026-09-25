package com.fserver.app.presentation.screens.source.details.model

sealed interface SourceDetailsIntent {
    data object RefreshRequested : SourceDetailsIntent
    data object SendNowClicked : SourceDetailsIntent
}
