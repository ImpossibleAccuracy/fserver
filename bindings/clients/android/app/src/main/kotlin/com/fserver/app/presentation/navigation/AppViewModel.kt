package com.fserver.app.presentation.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.data.AppSettingsStore
import com.fserver.app.domain.AuthManager
import com.fserver.app.presentation.composable.toUi
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.model.UnauthenticatedDestinations
import com.fserver.app.presentation.navigation.model.AppRootIntent
import com.fserver.app.presentation.navigation.model.AppRootState
import com.fserver.app.presentation.navigation.model.AppRootUiEffect
import com.fserver.core.FServerCore
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.device.IncomingConnection
import com.fserver.core.network.presence.PresenceController
import com.fserver.core.sync.SourcesController
import com.fserver.core.sync.progress.SourcePass
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import timber.log.Timber


/**
 * Owns where the app opens, and the connection prompts that can arrive over any screen.
 *
 * Being findable and looking for others are decided by `:core`; this only feeds it the two things
 * it cannot know - the user's settings, and whether anyone is in front of the app. Which methods
 * run in the background is the engine's call, and the discovery screen takes discovery over while
 * it is open, so a permission the user never saw asked never turns into an empty list.
 *
 * Incoming requests do live here, though, because they are not tied to a screen: the peer's
 * handshake is parked on the answer whatever the user happens to be looking at.
 */
class AppViewModel(
    private val sourcesController: SourcesController,
    private val devicesRepository: DevicesRepository,
    private val authManager: AuthManager,
    private val appSettings: AppSettingsStore,
    private val fServerCore: FServerCore,
) : ViewModel() {
    private val presence = fServerCore.presence

    private val presenceHandover = presence.handover()

    private val effectChannel = Channel<AppRootUiEffect>(Channel.BUFFERED)
    val uiEffects = effectChannel.receiveAsFlow()

    private val reportedFailures = mutableSetOf<String>()

    /** True while the app is in front of an authenticated user — advertising's other precondition. */
    private val isAppVisible = MutableStateFlow(false)

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
            incomingTransfer = null,
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

        // Keep serving in VM, cause serving should not be started for alarms/notifications/other background tasks
        fServerCore.startServing()?.invokeOnCompletion {
            Timber.i("FServerCore finished serving: ${it?.message ?: "no error"}")
        }

        // Paired device turning up on a scan syncs its sources without a tap.
        fServerCore.startAutoSync()?.invokeOnCompletion {
            Timber.i("FServerCore stopped auto-sync: ${it?.message ?: "no error"}")
        }

        viewModelScope.launch {
            combine(
                isAppVisible,
                appSettings.discoverable,
                appSettings.discoveryEnabled,
                ::Triple,
            ).collect { (visible, discoverable, discovery) ->
                presenceHandover.setAdvertising(visible && discoverable)
                presenceHandover.setDiscovery(
                    // TODO: research, how to run nearby connections here too
                    if (visible && discovery) PresenceController.BackgroundMethods else emptySet(),
                )
            }
        }

        viewModelScope.launch {
            sourcesController.progress.passes.collect(::reportFailedPasses)
        }

        presence.start()
    }

    fun onIntent(intent: AppRootIntent) {
        when (intent) {
            is AppRootIntent.AcceptIncomingConnection ->
                answer { it.accept() }

            is AppRootIntent.RejectIncomingConnection ->
                answer { it.reject() }

            is AppRootIntent.AcceptIncomingTransfer -> {}

            is AppRootIntent.RejectIncomingTransfer -> {}

            is AppRootIntent.AcceptPendingConfirmation ->
                devicesRepository.resolvePendingConfirmation(accept = true)

            is AppRootIntent.RejectPendingConfirmation ->
                devicesRepository.resolvePendingConfirmation(accept = false)

            is AppRootIntent.ForegroundStateChanged -> handleForegroundState(intent)
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

    /** Reports the precondition; `:core` decides what to do with it. */
    private fun handleForegroundState(intent: AppRootIntent.ForegroundStateChanged) {
        val isLifecycleForeground = intent.lifecycle.isAtLeast(Lifecycle.State.STARTED)
        val isAfterAuth = intent.destination != null &&
                intent.destination !is UnauthenticatedDestinations

        isAppVisible.value = isLifecycleForeground && isAfterAuth

        // Permissions changed/system toggle enabled, recheck
        presence.recheck()
    }

    /**
     * The catch-all for a pass that gave up. Why a *device* could not be reached is answered on
     * the files screen, off `DeviceReachability`; this is what is left over - a pass that broke
     * for some other reason, over whatever screen the user is on.
     *
     * TODO: a pass failure is still only a string, so this toasts the same line for every one of
     *  them. Type it the way a dial is typed - see `docs/TODO_LIST.md`.
     */
    private suspend fun reportFailedPasses(passes: List<SourcePass>) {
        val failed = passes
            .filterIsInstance<SourcePass.Local>()
            .filter { it.stage == SourcePass.Local.Stage.Failed }
            .map { it.sourceId }

        reportedFailures.retainAll(failed.toSet())

        failed.filterNot(reportedFailures::contains).forEach { sourceId ->
            reportedFailures.add(sourceId)
            effectChannel.send(AppRootUiEffect.SyncFailed)
        }
    }

    private fun computeStartDestination(profile: AuthManager.Profile?): Destination =
        when (profile) {
            null -> Destination.Onboarding
            else -> Destination.Files.List
        }

    override fun onCleared() {
        presenceHandover.close()
        runBlocking { presence.stop() }
    }
}
