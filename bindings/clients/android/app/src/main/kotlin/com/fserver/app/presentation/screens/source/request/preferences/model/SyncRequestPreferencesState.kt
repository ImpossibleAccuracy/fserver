package com.fserver.app.presentation.screens.source.request.preferences.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.screens.source.request.shared.model.SyncRequestUi
import com.fserver.app.presentation.screens.source.setup.conditions.model.SourceConditionsState

@Immutable
data class SyncRequestPreferencesState(
    val request: SyncRequestUi? = null,
    val isLoaded: Boolean = false,
    val wifiOnly: Boolean = true,
    val chargingOnly: Boolean = false,
    val limitFiles: Boolean = false,
    val maxFiles: Int = SourceConditionsState.DefaultMaxFiles,
    val limitSize: Boolean = false,
    val maxSizeGb: Int = SourceConditionsState.DefaultMaxSizeGb,
    val isAccepting: Boolean = false,
) {
    val isGone: Boolean get() = isLoaded && request == null

    val canAccept: Boolean get() = request != null && !isAccepting
}
