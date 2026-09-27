package com.fserver.app.presentation.screens.source.setup.conditions.model

import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesIntent

sealed interface SourceConditionsIntent {
    data object ExplainerAccepted : SourceConditionsIntent

    data class PreferencesChanged(val intent: SourcePreferencesIntent) : SourceConditionsIntent

    /** The form is answered — this is what starts the preparing step. */
    data object Confirmed : SourceConditionsIntent

    data object PreparingCancelled : SourceConditionsIntent

    /** Registering the source failed — try the same answers again. */
    data object RetryConfirmed : SourceConditionsIntent
}
