package com.fserver.app.presentation.screens.discovery.manual

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.screens.discovery.manual.model.ManualAddressIntent
import com.fserver.app.presentation.screens.discovery.manual.model.ManualAddressState
import com.fserver.core.Constants
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.info.model.PeerLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class ManualAddressViewModel(
    private val devicesRepository: DevicesRepository,
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

            ManualAddressIntent.ResultConsumed -> _state.update { it.copy(found = null) }
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

        val arguments = PeerLocator.Ip(host = current.host, port = port)

        viewModelScope.launch {
            _state.update { it.copy(isChecking = true, error = null) }

            devicesRepository
                .probe(arguments)
                .fold(
                    onSuccess = {
                        _state.update {
                            it.copy(
                                isChecking = false,
                                found = arguments,
                                error = null,
                            )
                        }
                    },
                    onFailure = { e ->
                        // TODO: add error messages parser util
                        _state.update {
                            it.copy(
                                isChecking = false,
                                error = ManualAddressState.Error.Unknown(e.localizedMessage)
                            )
                        }
                    }
                )
        }
    }
}
