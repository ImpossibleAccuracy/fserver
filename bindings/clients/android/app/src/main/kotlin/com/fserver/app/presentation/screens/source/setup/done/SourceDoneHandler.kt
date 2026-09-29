package com.fserver.app.presentation.screens.source.setup.done

import com.fserver.app.presentation.composable.model.peerOf
import com.fserver.app.presentation.composable.model.peers
import com.fserver.app.presentation.screens.source.setup.done.model.SourceDoneState
import com.fserver.app.presentation.screens.source.setup.shared.model.SourceSetupState
import com.fserver.app.util.stateInScreen
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.storage.RegisteredSourcesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

@OptIn(ExperimentalCoroutinesApi::class)
class SourceDoneHandler(
    private val sourcesRepository: RegisteredSourcesRepository,
    private val devicesRepository: DevicesRepository,

    flow: Flow<SourceSetupState>,
    scope: CoroutineScope,
) {
    val state: StateFlow<SourceDoneState?> = flow
        .map { it.sourceId }
        .distinctUntilChanged()
        .flatMapLatest { id -> if (id == null) flowOf(null) else stateOf(id) }
        .stateInScreen(scope, null)

    private fun stateOf(sourceId: String): Flow<SourceDoneState> = combine(
        sourcesRepository.observeById(sourceId),
        devicesRepository.peers(),
    ) { source, peers ->
        source ?: return@combine SourceDoneState()

        SourceDoneState.of(entry = source, peer = peers.peerOf(source.deviceId))
    }
}
