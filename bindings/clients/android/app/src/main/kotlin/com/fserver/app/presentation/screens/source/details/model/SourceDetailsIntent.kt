package com.fserver.app.presentation.screens.source.details.model

import android.net.Uri

sealed interface SourceDetailsIntent {
    data object RefreshRequested : SourceDetailsIntent
    data object SendNowClicked : SourceDetailsIntent
    data object DeleteConfirmed : SourceDetailsIntent
    data class Export(val uri: Uri) : SourceDetailsIntent
}
