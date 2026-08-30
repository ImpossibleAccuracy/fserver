package com.fserver.app.presentation.screens.source.access.model

import com.fserver.app.presentation.screens.source.access.SourceAccessGrant

sealed interface SourceAccessIntent {
    data class AccessAnswered(val grant: SourceAccessGrant) : SourceAccessIntent

    data object ScanCancelled : SourceAccessIntent
}
