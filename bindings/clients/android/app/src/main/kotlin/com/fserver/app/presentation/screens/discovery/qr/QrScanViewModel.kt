package com.fserver.app.presentation.screens.discovery.qr

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.core.domain.model.DeviceDetectionRequest
import com.fserver.core.domain.model.exception.DetectionFailedException
import com.fserver.core.domain.model.exception.MalformedQrException
import com.fserver.core.domain.repository.DeviceDetectionRepository
import com.fserver.app.presentation.screens.discovery.qr.model.QrScanIntent
import com.fserver.app.presentation.screens.discovery.qr.model.QrScanState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch


class QrScanViewModel(
    private val deviceDetectionRepository: DeviceDetectionRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(QrScanState())
    val state: StateFlow<QrScanState> = _state.asStateFlow()

    fun onIntent(intent: QrScanIntent) {
        when (intent) {
            is QrScanIntent.CodeScanned -> connect(intent.payload)
            QrScanIntent.ResultConsumed -> _state.update { it.copy(foundDeviceId = null) }
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

            try {
                val foundDevices = deviceDetectionRepository
                    .startDetection(
                        DeviceDetectionRequest.QrCode(payload)
                    )
                    .getOrThrow() // TODO

                when {
                    foundDevices.isEmpty() -> {
                        _state.update {
                            it.copy(
                                isConnecting = false,
                                foundDeviceId = null,
                                error = QrScanState.Error.Unreachable,
                            )
                        }
                    }

                    foundDevices.size == 1 -> {
                        val device = foundDevices.first()
                        _state.update {
                            it.copy(
                                isConnecting = false,
                                foundDeviceId = device.id,
                                error = null,
                            )
                        }
                    }

                    else -> {
                        // TODO: show devices picker
                    }
                }
            } catch (_: MalformedQrException) {
                _state.update { it.copy(error = QrScanState.Error.MalformedCode) }
            } catch (e: DetectionFailedException) {
                // TODO: add error messages parser util
                _state.update { it.copy(error = QrScanState.Error.Unknown(e.localizedMessage)) }
            }
        }
    }
}
