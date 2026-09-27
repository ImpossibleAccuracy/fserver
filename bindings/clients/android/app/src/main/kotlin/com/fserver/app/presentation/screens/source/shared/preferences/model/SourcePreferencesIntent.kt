package com.fserver.app.presentation.screens.source.shared.preferences.model

sealed interface SourcePreferencesIntent {
    data class WifiOnlyToggled(val enabled: Boolean) : SourcePreferencesIntent

    data class ChargingOnlyToggled(val enabled: Boolean) : SourcePreferencesIntent

    data class LimitFilesToggled(val enabled: Boolean) : SourcePreferencesIntent

    data class MaxFilesStepped(val steps: Int) : SourcePreferencesIntent

    data class LimitSizeToggled(val enabled: Boolean) : SourcePreferencesIntent

    data class SizePresetSelected(val gb: Int?) : SourcePreferencesIntent

    data class MaxSizeStepped(val steps: Int) : SourcePreferencesIntent

    data class ConflictResolutionSelected(
        val resolution: ConflictResolutionUi,
    ) : SourcePreferencesIntent

    data class UploadScopeSelected(val scope: UploadScopeUi) : SourcePreferencesIntent

    data class CriterionSelected(val criterion: EvictCriterionUi) : SourcePreferencesIntent

    data class DaysStepped(val steps: Int) : SourcePreferencesIntent

    data class SizeThresholdStepped(val steps: Int) : SourcePreferencesIntent
}
