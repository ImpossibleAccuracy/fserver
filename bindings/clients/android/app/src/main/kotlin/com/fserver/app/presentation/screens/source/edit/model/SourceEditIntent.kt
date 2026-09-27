package com.fserver.app.presentation.screens.source.edit.model

import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesIntent

sealed interface SourceEditIntent {
    data class PreferencesChanged(val intent: SourcePreferencesIntent) : SourceEditIntent

    data object Saved : SourceEditIntent
}
