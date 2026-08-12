package com.fserver.app.presentation.navigation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.model.IncomingConnectionUi
import com.fserver.app.presentation.model.toUi
import com.fserver.app.presentation.navigation.model.NavigationState
import com.fserver.core.domain.repository.DevicesRepository
import com.fserver.net.connection.ConnectionManager
import com.fserver.net.session.CloseReason
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Owns where the app opens, and the connection prompts that can arrive over any screen.
 *
 * It used to start every automatic detection method as soon as the available capability set
 * changed. It no longer does: discovery belongs to the screen the user opened for it, which asks
 * for permissions per method and scans only once the user presses the button. A background pass
 * started here would turn a permission the user never saw asked into an empty list.
 *
 * Incoming requests do live here, though, because they are not tied to a screen: the peer's
 * handshake is parked on the answer whatever the user happens to be looking at.
 */
class AppViewModel(
    private val devicesRepository: DevicesRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(
        NavigationState(
            startDestination = Destination.Onboarding
        )
    )
    val state = _state.asStateFlow()

    // Queued rather than replaced: two devices can knock at once, and dropping one silently
    // leaves its user watching a spinner that will only ever time out.
    private val pending = MutableStateFlow<List<ConnectionManager.IncomingRequest>>(emptyList())

    val incomingConnection = pending
        .map { it.firstOrNull()?.toUi() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            devicesRepository.incoming.collect { request ->
                Timber.i("Incoming connection request from ${request.peer.advertisedName} via ${request.transport}")
                pending.update { it + request }
            }
        }
    }

    fun acceptIncoming() = answer {
        it.accept().onFailure { error -> Timber.w(error, "could not accept the connection") }
    }

    fun declineIncoming() = answer { it.reject(CloseReason.RejectedByUser) }

    /**
     * Takes the request off the queue first: accepting is a network round trip, and leaving it on
     * screen until that returns invites a second tap on a decision already made.
     */
    private fun answer(verdict: suspend (ConnectionManager.IncomingRequest) -> Unit) {
        val request = pending.value.firstOrNull() ?: return
        pending.update { it.drop(1) }

        viewModelScope.launch {
            runCatching { verdict(request) }
                .onFailure { Timber.w(it, "could not answer ${request.peer.advertisedName}") }
        }
    }

    override fun onCleared() {
        // TODO: stop discovery and advertising
    }
}
