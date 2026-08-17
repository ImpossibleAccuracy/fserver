package com.fserver.app.presentation.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.domain.AuthManager
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.model.UnauthenticatedDestinations
import com.fserver.app.presentation.model.toUi
import com.fserver.app.presentation.navigation.model.AppRootIntent
import com.fserver.app.presentation.navigation.model.AppRootState
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.device.IncomingConnection
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
    private val authManager: AuthManager,
) : ViewModel() {
    private val advertisingService = devicesRepository.advertisingServiceLease()

    private val startDestination = MutableStateFlow<Destination?>(null)

    // Queued rather than replaced: two devices can knock at once, and dropping one silently
    // leaves its user watching a spinner that will only every time out.
    private val pending =
        MutableStateFlow<List<IncomingConnection>>(emptyList())

    val state: StateFlow<AppRootState?> = combine(
        startDestination,
        pending,
        devicesRepository.pendingConfirmation,
    ) { destination, pending, pendingConfirmation ->
        destination ?: return@combine null

        AppRootState(
            startDestination = destination,
            incomingConnection = pending.firstOrNull()?.toUi(),
            pendingConfirmation = pendingConfirmation?.toUi(),
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = null,
    )

    init {
        viewModelScope.launch {
            devicesRepository.incoming.collect { request ->
                Timber.i("Incoming connection request from ${request.deviceName} via ${request.transport}")
                pending.update { it + request }
            }
        }

        viewModelScope.launch {
            authManager.profile
                .collect { profile ->
                    val destination = computeStartDestination(profile)
                    if (!startDestination.compareAndSet(null, destination)) {
                        // TODO: manually navigate to computed destination
                    }
                }
        }
    }

    fun onIntent(intent: AppRootIntent) {
        when (intent) {
            is AppRootIntent.AcceptIncomingConnection ->
                answer { it.accept() }

            is AppRootIntent.RejectIncomingConnection ->
                answer { it.reject() }

            is AppRootIntent.AcceptPendingConfirmation ->
                devicesRepository.resolvePendingConfirmation(accept = true)

            is AppRootIntent.RejectPendingConfirmation ->
                devicesRepository.resolvePendingConfirmation(accept = false)

            is AppRootIntent.ForegroundStateChanged -> viewModelScope.launch {
                handleForegroundState(intent)
            }
        }
    }

    /**
     * Takes the request off the queue first: accepting is a network round trip, and leaving it on
     * screen until that returns invites a second tap on a decision already made.
     */
    private fun answer(verdict: suspend (IncomingConnection) -> Unit) {
        val request = pending.value.firstOrNull() ?: return
        pending.update { it.drop(1) }

        viewModelScope.launch {
            runCatching { verdict(request) }
                .onFailure { Timber.w(it, "could not answer ${request.deviceName}") }
        }
    }

    private suspend fun handleForegroundState(intent: AppRootIntent.ForegroundStateChanged) {
        val isLifecycleForeground = intent.lifecycle.isAtLeast(Lifecycle.State.STARTED)
        val isAfterAuth = intent.destination != null &&
                intent.destination !is UnauthenticatedDestinations

        // app should provide ability to enable/disable advertising
        if (isLifecycleForeground && isAfterAuth) {
            advertisingService.start()
        } else {
            advertisingService.stop()
        }
    }

    private fun computeStartDestination(profile: AuthManager.Profile?): Destination =
        when (profile) {
            null -> Destination.Onboarding
            else -> Destination.Files.List
        }

    override fun onCleared() {
        runBlocking { advertisingService.stop() }
    }
}
