package com.fserver.app.presentation.screens.source.setup.conditions.model

import com.fserver.app.presentation.screens.source.setup.conditions.model.EvictCriterionUi
import com.fserver.app.presentation.screens.source.setup.conditions.model.HostRightsUi
import com.fserver.app.presentation.screens.source.setup.conditions.model.UploadScopeUi

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

    /** Registering the source failed — try the same answers again. */
    data object RetryConfirmed : SourceConditionsIntent
}
