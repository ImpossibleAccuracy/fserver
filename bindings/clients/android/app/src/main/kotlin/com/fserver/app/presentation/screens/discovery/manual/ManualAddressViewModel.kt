package com.fserver.app.presentation.screens.discovery.manual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.domain.Constants
import com.fserver.app.domain.model.DeviceDetectionRequest
import com.fserver.app.domain.model.exception.DetectionFailedException
import com.fserver.app.domain.repository.DeviceDetectionRepository
import com.fserver.app.presentation.screens.discovery.manual.model.ManualAddressIntent
import com.fserver.app.presentation.screens.discovery.manual.model.ManualAddressState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ManualAddressViewModel(
    private val deviceDetectionRepository: DeviceDetectionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(ManualAddressState())
    val state: StateFlow<ManualAddressState> = _state.asStateFlow()

    fun onIntent(intent: ManualAddressIntent) {
        when (intent) {
            // Typing invalidates the previous verdict: an error about the old address must
            // not sit under a field the user has since changed.
            is ManualAddressIntent.HostChanged -> _state.update {
                it.copy(host = intent.host, error = null)
            }

            is ManualAddressIntent.PortChanged -> _state.update {
                it.copy(port = intent.port.filter(Char::isDigit), error = null)
            }

            ManualAddressIntent.ConnectClicked -> connect()

            ManualAddressIntent.ResultConsumed -> _state.update { it.copy(foundDeviceId = null) }
        }
    }

    private fun connect() {
        val current = _state.value
        if (!current.canConnect) return

        val port = current.port.takeIf { it.isNotBlank() }?.toIntOrNull()
        if (current.port.isNotBlank() && (port == null || port !in Constants.VALID_PORT_RANGE)) {
            _state.update { it.copy(error = ManualAddressState.Error.InvalidPort) }
            return
        }

        viewModelScope.launch {
            _state.update { it.copy(isChecking = true, error = null) }

            try {
                val foundDevices = deviceDetectionRepository
                    .startDetection(
                        DeviceDetectionRequest.ByManualAddress(
                            ipAddress = current.host.trim(),
                            port = port,
                        )
                    )
                    .getOrThrow() // TODO

                val device = foundDevices.firstOrNull()
                _state.update {
                    it.copy(
                        isChecking = false,
                        foundDeviceId = device?.id,
                        error = ManualAddressState.Error.Unreachable.takeIf { device == null },
                    )
                }
            } catch (e: DetectionFailedException) {
                // TODO: add error messages parser util
                _state.update { it.copy(error = ManualAddressState.Error.Unknown(e.localizedMessage)) }
            }
        }
    }
}
