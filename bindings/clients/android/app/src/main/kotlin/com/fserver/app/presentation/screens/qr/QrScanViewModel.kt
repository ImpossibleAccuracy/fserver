package com.fserver.app.presentation.screens.qr

import androidx.lifecycle.ViewModel
import com.fserver.app.presentation.screens.qr.model.QrScanState
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * The QR route to a server. The code carries address, access method and key fingerprint
 * at once, so this path skips the manual fingerprint screen: the comparison already
 * happened inside the code, against a value the user's own server printed.
 *
 * Nothing to reduce yet — the camera is not bound, so the only state is that fact.
 */
class QrScanViewModel : ViewModel() {

    private val _state = MutableStateFlow(QrScanState())
    val state: StateFlow<QrScanState> = _state.asStateFlow()
}
