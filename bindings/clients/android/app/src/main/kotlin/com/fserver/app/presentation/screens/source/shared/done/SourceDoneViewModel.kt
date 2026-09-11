package com.fserver.app.presentation.screens.source.shared.done

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.source.shared.done.model.SourceDoneState
import com.fserver.core.storage.RegisteredSourcesRepository
import com.fserver.core.storage.TrustedDevicesRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

class SourceDoneViewModel(
    private val key: Destination.Source.Done,
    private val sourcesRepository: RegisteredSourcesRepository,
    private val trustedDevices: TrustedDevicesRepository,
) : ViewModel() {

    val state: StateFlow<SourceDoneState> = combine(
        sourcesRepository.observeById(key.sourceId),
        trustedDevices.devices,
    ) { source, devices ->
        source ?: return@combine SourceDoneState()

        SourceDoneState.of(entry = source, devices = devices)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SourceDoneState(),
    )
}
