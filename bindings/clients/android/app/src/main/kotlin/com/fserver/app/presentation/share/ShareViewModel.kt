package com.fserver.app.presentation.share

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.composable.model.PeerUi
import com.fserver.app.presentation.composable.model.peers
import com.fserver.app.presentation.share.model.ShareState
import com.fserver.app.presentation.share.model.ShareUiEffect
import com.fserver.app.presentation.shared.error.toAppError
import com.fserver.app.util.stateInScreen
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.oneshot.OneShotTransfersController
import com.fserver.core.storage.TrustedDevicesRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** Files another app shared, on their way to a device the user picks. */
class ShareViewModel(
    devicesRepository: DevicesRepository,
    trustedDevices: TrustedDevicesRepository,
    private val controller: OneShotTransfersController,
) : ViewModel() {
    private var uris: List<Uri> = emptyList()

    private val progress = MutableStateFlow(ShareState())

    private val effects = Channel<ShareUiEffect>(Channel.BUFFERED)
    val uiEffects = effects.receiveAsFlow()

    val state: StateFlow<ShareState> = combine(
        progress,
        devicesRepository.peers(),
        trustedDevices.devices,
    ) { progress, peers, trusted ->
        val trustedIds = trusted.mapTo(mutableSetOf()) { it.deviceId }

        progress.copy(
            devices = peers.values
                .filter { it.online || it.id in trustedIds }
                .sortedWith(compareByDescending<PeerUi> { it.online }.thenBy { it.name }),
        )
    }.stateInScreen(viewModelScope, ShareState())

    fun setUris(uris: List<Uri>) {
        this.uris = uris
        progress.update { it.copy(fileCount = uris.size) }
    }

    fun send(deviceId: String) {
        if (progress.value.sending) return
        progress.update { it.copy(sending = true, error = null) }

        viewModelScope.launch {
            controller.sendShared(deviceId, uris)
                .onSuccess { effects.send(ShareUiEffect.Offered(it.files.size, it.peer.displayName)) }
                .onFailure { error -> progress.update { it.copy(sending = false, error = error.toAppError()) } }
        }
    }
}
