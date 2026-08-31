package com.fserver.app.presentation.screens.discovery.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.screens.discovery.qr.model.QrScanIntent
import com.fserver.app.presentation.screens.discovery.qr.model.QrScanState
import com.fserver.common.exception.MalformedQrException
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.info.model.PeerLocator
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


class QrScanViewModel(
    private val devicesRepository: DevicesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(QrScanState())
    val state: StateFlow<QrScanState> = _state.asStateFlow()

    fun onIntent(intent: QrScanIntent) {
        when (intent) {
            is QrScanIntent.CodeScanned -> connect(intent.payload)
            QrScanIntent.ResultConsumed -> _state.update { it.copy(found = null) }
        }
    }

    private fun connect(payload: String) {
        // Decoder keeps firing while the code is in frame; one attempt at a time.
        if (_state.value.isConnecting) return

        viewModelScope.launch {
            _state.update {
                it.copy(
                    isConnecting = true,
                    error = null,
                )
            }

            devicesRepository
                .probe(PeerLocator.QrPayload(payload))
                .fold(
                    onSuccess = {
                        _state.update {
                            it.copy(
                                isConnecting = false,
                                found = PeerLocator.QrPayload(payload),
                                error = null,
                            )
                        }
                    },
                    onFailure = { e ->
                        if (e is MalformedQrException) {
                            _state.update {
                                it.copy(
                                    isConnecting = false,
                                    error = QrScanState.Error.MalformedCode
                                )
                            }
                        } else {
                            // TODO: add error messages parser util
                            _state.update {
                                it.copy(
                                    isConnecting = false,
                                    error = QrScanState.Error.Unknown(e.localizedMessage)
                                )
                            }
                        }
                    }
                )
        }
    }
}
