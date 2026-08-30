package com.fserver.app.presentation.screens.source.conditions.model

import com.fserver.app.presentation.screens.source.shared.EvictCriterionUi
import com.fserver.app.presentation.screens.source.shared.HostRightsUi
import com.fserver.app.presentation.screens.source.shared.UploadScopeUi

sealed interface SourceConditionsIntent {
    data object ExplainerAccepted : SourceConditionsIntent

    data class UploadScopeSelected(val scope: UploadScopeUi) : SourceConditionsIntent
    data class WifiOnlyToggled(val enabled: Boolean) : SourceConditionsIntent
    data class ChargingOnlyToggled(val enabled: Boolean) : SourceConditionsIntent

    data class CriterionSelected(val criterion: EvictCriterionUi) : SourceConditionsIntent
    data class DaysStepped(val steps: Int) : SourceConditionsIntent
    data class KeepPinnedToggled(val enabled: Boolean) : SourceConditionsIntent

    data class HostRightsSelected(val rights: HostRightsUi) : SourceConditionsIntent

    /** The form is answered — this is what starts the preparing step. */
    data object Confirmed : SourceConditionsIntent

    data object PreparingCancelled : SourceConditionsIntent
}
