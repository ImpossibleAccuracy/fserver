package com.fserver.app.presentation.screens.source.shared.done

import com.fserver.app.util.stateInScreen
import com.fserver.core.network.device.DevicesRepository
import com.fserver.app.presentation.composable.model.peers
import com.fserver.app.presentation.composable.model.peerOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.screens.source.shared.done.model.SourceDoneState
import com.fserver.core.storage.RegisteredSourcesRepository
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine

class SourceDoneViewModel(
    private val key: Destination.Source.Done,
    private val sourcesRepository: RegisteredSourcesRepository,
    devicesRepository: DevicesRepository,
) : ViewModel() {

    val state: StateFlow<SourceDoneState> = combine(
        sourcesRepository.observeById(key.sourceId),
        devicesRepository.peers(),
    ) { source, peers ->
        source ?: return@combine SourceDoneState()

        SourceDoneState.of(entry = source, peer = peers.peerOf(source.deviceId))
    }.stateInScreen(viewModelScope, SourceDoneState())
}
