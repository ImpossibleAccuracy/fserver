package com.fserver.app.presentation.screens.source.setup.pick.model

import com.fserver.app.presentation.screens.source.shared.model.SourceKindUi

sealed interface SourcePickIntent {
    data object MoreToggled : SourcePickIntent

    data class UnavailablePicked(val kind: SourceKindUi) : SourcePickIntent
}
