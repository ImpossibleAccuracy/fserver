package com.fserver.app.presentation.screens.source.shared

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.screens.source.access.SourceAccessHandler
import com.fserver.app.presentation.screens.source.conditions.SourceConditionsHandler
import com.fserver.app.presentation.screens.source.done.SourceDoneHandler
import com.fserver.app.presentation.screens.source.mode.SourceModeHandler
import com.fserver.app.presentation.screens.source.pick.SourcePickHandler
import com.fserver.app.presentation.screens.source.shared.model.SourceFlowState
import com.fserver.app.presentation.screens.source.shared.model.SourceKindUi
import com.fserver.core.network.device.DevicesRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

class SourceFlowViewModel(
    private val devicesRepository: DevicesRepository,
) : ViewModel() {

    private val editable = MutableStateFlow(SourceFlowState())

    val state: StateFlow<SourceFlowState> = editable.asStateFlow()

    val isStarted: StateFlow<Boolean> = editable
        .map { it.kind != null }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = editable.value.kind != null,
        )

    val pick = SourcePickHandler(viewModelScope)

    val access = SourceAccessHandler(editable, viewModelScope)

    val mode = SourceModeHandler(editable, viewModelScope)

    val conditions = SourceConditionsHandler(devicesRepository, editable, viewModelScope)

    val done = SourceDoneHandler(devicesRepository, editable, viewModelScope)

    fun start(kind: SourceKindUi) {
        access.reset()
        mode.reset()
        conditions.reset()
        editable.value = SourceFlowState(kind = kind)
    }

    fun onDeviceSelected(deviceId: String) {
        editable.update { it.copy(targetDeviceId = deviceId) }
    }
}
