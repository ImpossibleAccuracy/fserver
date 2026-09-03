package com.fserver.app.presentation.screens.request.details.model

sealed interface SyncRequestDetailsIntent {
    data object Declined : SyncRequestDetailsIntent
}
