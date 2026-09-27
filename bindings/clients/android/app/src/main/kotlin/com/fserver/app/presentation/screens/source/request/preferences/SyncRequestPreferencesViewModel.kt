package com.fserver.app.presentation.screens.source.request.preferences

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.source.request.preferences.model.SyncRequestPreferencesIntent
import com.fserver.app.presentation.screens.source.request.preferences.model.SyncRequestPreferencesState
import com.fserver.app.presentation.screens.source.request.preferences.model.SyncRequestPreferencesUiEffect
import com.fserver.app.presentation.screens.source.request.shared.model.toUi
import com.fserver.app.presentation.screens.source.setup.conditions.model.SourceConditionsState
import com.fserver.app.presentation.screens.source.shared.model.toLocation
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.common.model.FileSize
import com.fserver.core.storage.TrustedDevicesRepository
import com.fserver.core.sync.SourcesController
import com.fserver.core.sync.model.SourceEntry
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SyncRequestPreferencesViewModel(
    private val key: Destination.Source.Request.Preferences,
    private val sourcesController: SourcesController,
    private val trustedDevices: TrustedDevicesRepository,
    private val reporter: ErrorReporter,
) : ViewModel() {

    private val editable = MutableStateFlow(Editable())

    private val effects = Channel<SyncRequestPreferencesUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    val state: StateFlow<SyncRequestPreferencesState> = combine(
        sourcesController.incomingRequests,
        trustedDevices.devices,
        editable,
    ) { requests, devices, local ->
        SyncRequestPreferencesState(
            request = requests.firstOrNull { it.sourceId == key.sourceId }?.toUi(devices),
            isLoaded = true,
            wifiOnly = local.wifiOnly,
            chargingOnly = local.chargingOnly,
            limitFiles = local.limitFiles,
            maxFiles = local.maxFiles,
            limitSize = local.limitSize,
            maxSizeGb = local.maxSizeGb,
            isAccepting = local.accepting,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SyncRequestPreferencesState(),
    )

    fun onIntent(intent: SyncRequestPreferencesIntent) {
        when (intent) {
            is SyncRequestPreferencesIntent.WifiOnlyToggled ->
                editable.update { it.copy(wifiOnly = intent.enabled) }

            is SyncRequestPreferencesIntent.ChargingOnlyToggled ->
                editable.update { it.copy(chargingOnly = intent.enabled) }

            is SyncRequestPreferencesIntent.LimitFilesToggled ->
                editable.update { it.copy(limitFiles = intent.enabled) }

            is SyncRequestPreferencesIntent.MaxFilesStepped ->
                editable.update { it.copy(maxFiles = stepMaxFiles(it.maxFiles, intent.steps)) }

            is SyncRequestPreferencesIntent.LimitSizeToggled ->
                editable.update { it.copy(limitSize = intent.enabled) }

            is SyncRequestPreferencesIntent.MaxSizeStepped ->
                editable.update { it.copy(maxSizeGb = stepMaxSize(it.maxSizeGb, intent.steps)) }

            SyncRequestPreferencesIntent.Accepted -> accept()
        }
    }

    private fun accept() {
        if (editable.value.accepting) return
        editable.update { it.copy(accepting = true) }

        viewModelScope.launch {
            sourcesController.acceptRequest(
                sourceId = key.sourceId,
                location = key.location.toLocation(key.sourceId),
                preferences = editable.value.toPreferences(),
            ).fold(
                onSuccess = { effects.send(SyncRequestPreferencesUiEffect.NavigateToProgress) },
                onFailure = { failure ->
                    reporter.report(failure, "Could not accept source ${key.sourceId}")
                },
            )

            editable.update { it.copy(accepting = false) }
        }
    }

    private fun stepMaxFiles(current: Int, steps: Int): Int =
        (current + steps * SourceConditionsState.MaxFilesStep).coerceIn(
            SourceConditionsState.MinMaxFiles,
            SourceConditionsState.MaxMaxFiles,
        )

    private fun stepMaxSize(current: Int, steps: Int): Int =
        (current + steps * SourceConditionsState.MaxSizeStepGb).coerceIn(
            SourceConditionsState.MinMaxSizeGb,
            SourceConditionsState.MaxMaxSizeGb,
        )

    private fun Editable.toPreferences() = SourceEntry.Preferences(
        deviceConstraints = SourceEntry.Preferences.DeviceConstraints(
            wifiRequired = wifiOnly,
            chargingRequired = chargingOnly,
        ),
        fileLimits = SourceEntry.Preferences.FileLimits(
            maxFiles = maxFiles.takeIf { limitFiles },
            maxTotalSize = FileSize(maxSizeGb.toLong() * BytesInGb).takeIf { limitSize },
        ),
    )

    private data class Editable(
        val wifiOnly: Boolean = true,
        val chargingOnly: Boolean = false,
        val limitFiles: Boolean = false,
        val maxFiles: Int = SourceConditionsState.DefaultMaxFiles,
        val limitSize: Boolean = false,
        val maxSizeGb: Int = SourceConditionsState.DefaultMaxSizeGb,
        val accepting: Boolean = false,
    )

    private companion object {
        const val BytesInGb = 1024L * 1024 * 1024
    }
}
