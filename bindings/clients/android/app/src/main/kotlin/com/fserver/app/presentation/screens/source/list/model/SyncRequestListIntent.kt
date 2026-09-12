package com.fserver.app.presentation.screens.source.list.model

sealed interface SyncRequestListIntent {
    data class Declined(val sourceId: String) : SyncRequestListIntent
    data object DeclinedAll : SyncRequestListIntent
}
