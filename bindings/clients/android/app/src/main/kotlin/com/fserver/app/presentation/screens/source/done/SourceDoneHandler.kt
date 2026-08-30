package com.fserver.app.presentation.screens.source.done

import com.fserver.app.presentation.screens.source.done.model.SourceDoneState
import com.fserver.app.presentation.screens.source.shared.model.SourceFlowState
import com.fserver.core.network.device.DevicesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

@OptIn(ExperimentalCoroutinesApi::class)
class SourceDoneHandler(
    private val devicesRepository: DevicesRepository,

    private val flow: MutableStateFlow<SourceFlowState>,
    private val scope: CoroutineScope,
) {
    private val targetDevice = flow.map { it.targetDeviceId }
        .flatMapLatest {
            if (it == null) flowOf(null)
            else devicesRepository.device(it)
        }

    val state: StateFlow<SourceDoneState?> = combine(flow, targetDevice) { shared, device ->
        SourceDoneState.of(
            kind = shared.kind ?: return@combine null,
            mode = shared.mode ?: return@combine null,
            source = shared.source ?: return@combine null,
            conditions = shared.conditions ?: return@combine null,
            target = device,
        )
    }.stateIn(scope, SharingStarted.WhileSubscribed(5_000), null)
}
