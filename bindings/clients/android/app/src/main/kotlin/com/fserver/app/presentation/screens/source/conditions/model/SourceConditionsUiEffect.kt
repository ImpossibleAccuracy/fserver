package com.fserver.app.presentation.screens.source.conditions.model

import com.fserver.app.presentation.screens.source.shared.model.SourceSummaryUi

sealed interface SourceConditionsUiEffect {
    data class NavigateToDone(val summary: SourceSummaryUi) : SourceConditionsUiEffect
}
