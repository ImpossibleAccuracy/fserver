package com.fserver.app.presentation.screens.source.setup.access.model

import com.fserver.app.presentation.screens.source.setup.access.SourceAccessGrant

sealed interface SourceAccessIntent {
    data class AccessAnswered(val grant: SourceAccessGrant) : SourceAccessIntent

    data object ScanCancelled : SourceAccessIntent

    /** Whole device only: narrows the source to one folder and walks it again. */
    data object DirectoryConfirmed : SourceAccessIntent

    data object Confirmed : SourceAccessIntent
}
