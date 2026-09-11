package com.fserver.app.presentation.screens.source.request.details.model

sealed interface SyncRequestDetailsIntent {
    data object Declined : SyncRequestDetailsIntent
}
