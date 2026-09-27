package com.fserver.app.presentation.screens.source.edit

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.source.edit.model.SourceEditIntent
import com.fserver.app.presentation.screens.source.edit.model.SourceEditState
import com.fserver.app.presentation.screens.source.edit.model.SourceEditUiEffect
import com.fserver.app.presentation.screens.source.shared.model.latest
import com.fserver.app.presentation.screens.source.shared.model.toUi
import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesIntent
import com.fserver.app.presentation.screens.source.shared.preferences.model.SourcePreferencesUi
import com.fserver.app.presentation.screens.source.shared.preferences.model.reduce
import com.fserver.app.presentation.screens.source.shared.preferences.model.toPreferences
import com.fserver.app.presentation.screens.source.shared.preferences.model.toSyncMode
import com.fserver.app.presentation.shared.error.ErrorReporter
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.storage.RegisteredSourcesRepository
import com.fserver.core.storage.TrustedDevicesRepository
import com.fserver.core.sync.SourcesController
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class SourceEditViewModel(
    private val key: Destination.Files.SourceEdit,
    private val sourcesController: SourcesController,
    private val registeredSources: RegisteredSourcesRepository,
    trustedDevices: TrustedDevicesRepository,
    devicesRepository: DevicesRepository,
    private val reporter: ErrorReporter,
) : ViewModel() {

    private val editable = MutableStateFlow(Editable())

    private val effects = Channel<SourceEditUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    val state: StateFlow<SourceEditState> = combine(
        registeredSources.observeById(key.sourceId),
        registeredSources.observeTotals(key.sourceId),
        trustedDevices.devices,
        devicesRepository.devices.connected,
        editable,
    ) { source, totals, trusted, connected, local ->
        if (source == null) return@combine SourceEditState(isLoading = false)

        val initial = SourcePreferencesUi.build(source)
        val preferences = local.preferences ?: initial
        val total = if (source.role == SourceEntry.Role.Initiator) totals.here else totals.peer

        SourceEditState(
            isLoading = false,
            label = source.label,
            peerName = connected.firstOrNull { it.deviceId == source.deviceId }?.displayName
                ?: trusted.latest(source.deviceId)?.displayName
                ?: source.deviceId,
            role = source.role.toUi(),
            preferences = preferences,
            sourceFiles = total.count,
            sourceBytes = total.size.bytes,
            isChanged = preferences != initial,
            isSaving = local.saving,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SourceEditState(),
    )

    fun onIntent(intent: SourceEditIntent) {
        when (intent) {
            is SourceEditIntent.PreferencesChanged -> changePreferences(intent.intent)
            SourceEditIntent.Saved -> save()
        }
    }

    private fun changePreferences(intent: SourcePreferencesIntent) {
        val current = state.value
        if (current.isLoading) return
        editable.update { it.copy(preferences = current.preferences.reduce(intent)) }
    }

    private fun save() {
        if (!state.value.canSave) return
        val preferences = state.value.preferences
        editable.update { it.copy(saving = true) }

        viewModelScope.launch {
            runCatching {
                val source = checkNotNull(registeredSources.observeById(key.sourceId).first()) {
                    "Source ${key.sourceId} is gone"
                }

                sourcesController.updateSource(
                    id = source.id,
                    syncMode = source.editedMode(preferences),
                    preferences = preferences.toPreferences(),
                ).getOrThrow()
            }.fold(
                onSuccess = { effects.send(SourceEditUiEffect.NavigateBack) },
                onFailure = { reporter.report(it, "Could not update source ${key.sourceId}") },
            )
            editable.update { it.copy(saving = false) }
        }
    }

    private data class Editable(
        val preferences: SourcePreferencesUi? = null,
        val saving: Boolean = false,
    )
}

private fun SourceEntry.editedMode(preferences: SourcePreferencesUi): SyncMode {
    val initial = SourcePreferencesUi.build(this)
    val untouched = preferences.conflicts == initial.conflicts &&
        preferences.upload == initial.upload &&
        preferences.eviction == initial.eviction

    if (role != SourceEntry.Role.Initiator || untouched) return syncMode

    return preferences.toSyncMode(syncMode.toUi())
}
