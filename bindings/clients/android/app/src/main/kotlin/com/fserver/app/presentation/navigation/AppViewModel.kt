package com.fserver.app.presentation.navigation

import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.data.AppSettingsStore
import com.fserver.app.domain.AuthManager
import com.fserver.app.presentation.shared.browser.model.FileBrowserUi
import com.fserver.app.presentation.composable.toUi
import com.fserver.app.presentation.shared.error.ErrorBus
import com.fserver.app.presentation.shared.error.toAppError
import com.fserver.app.presentation.model.Destination
import com.fserver.app.presentation.model.UnauthenticatedDestinations
import com.fserver.app.presentation.navigation.model.AppRootIntent
import com.fserver.app.presentation.navigation.model.AppRootState
import com.fserver.core.FServerCore
import com.fserver.core.lifecycle.LifecycleController
import com.fserver.core.lifecycle.network.PresenceController
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.device.IncomingConnection
import com.fserver.core.sync.SourcesController
import com.fserver.core.sync.progress.SourcePass
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
    private val lifecycleController: LifecycleController,
    private val errorBus: com.fserver.app.presentation.shared.error.ErrorBus,
) : ViewModel() {
    private val presenceHandover = lifecycleController.presenceHandover()

    /**
     * Every failure the app raised, wherever it was raised. Collected once at the root — see
     * [com.fserver.app.presentation.error.ErrorHandler].
     */
    val errors = errorBus.errors

    private val reportedFailures = mutableSetOf<String>()

    /** True while the app is in front of an authenticated user — advertising's other precondition. */
    private val isAppVisible = MutableStateFlow(false)

    private val startDestination = MutableStateFlow<Destination?>(null)

    // Queued rather than replaced: two devices can knock at once, and dropping one silently
    // leaves its user watching a spinner that will only every time out.
    private val pending =
        MutableStateFlow<List<IncomingConnection>>(emptyList())

    private val viewedFile = MutableStateFlow<FileBrowserUi.File?>(null)

    val state: StateFlow<AppRootState?> = combine(
        startDestination,
        pending,
        devicesRepository.pendingConfirmation,
        viewedFile,
    ) { destination, pending, pendingConfirmation, viewedFile ->
        destination ?: return@combine null

        AppRootState(
            startDestination = destination,
            incomingConnection = pending.firstOrNull()?.toUi(),
            pendingConfirmation = pendingConfirmation?.toUi(),
            incomingTransfer = null,
            viewedFile = viewedFile,
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
        viewModelScope.launch {
            fServerCore.startServing()
                // Refused before it bound: a permission or the network, and the report says which.
                .onFailure { errorBus.report(it, "could not start serving") }
                .getOrNull()
                ?.invokeOnCompletion {
                    Timber.i("FServerCore finished serving: ${it?.message ?: "no error"}")
                }
        }

        // Paired device turning up on a scan syncs its sources without a tap.
        lifecycleController.startAutoSync()?.invokeOnCompletion {
            Timber.i("FServerCore stopped auto-sync: ${it?.message ?: "no error"}")
        }

        // ...and one dialling in is answered without one either, so a pass does not wait on the
        // user. Only for a device already trusted with a source registered against it; everyone
        // else still arrives below as a prompt.
        lifecycleController.startAutoAccept()

        lifecycleController.presence.start()?.invokeOnCompletion {
            Timber.i("FServerCore stopped presence: ${it?.message ?: "no error"}")
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

            is AppRootIntent.ViewFile -> viewedFile.value = intent.file

            is AppRootIntent.CloseFileViewer -> viewedFile.value = null

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
                .onFailure { errorBus.report(it, "could not answer ${request.deviceName}") }
        }
    }

    /** Reports the precondition; `:core` decides what to do with it. */
    private fun handleForegroundState(intent: AppRootIntent.ForegroundStateChanged) {
        val isLifecycleForeground = intent.lifecycle.isAtLeast(Lifecycle.State.STARTED)
        val isAfterAuth = intent.destination != null &&
                intent.destination !is UnauthenticatedDestinations

        isAppVisible.value = isLifecycleForeground && isAfterAuth

        // Permissions changed/system toggle enabled, recheck
        lifecycleController.presence.recheck()
    }

    /**
     * The catch-all for a pass that gave up. Why a *device* could not be reached is answered on
     * the files screen, off `DeviceReachability`; this is what is left over - a pass that broke
     * for some other reason, over whatever screen the user is on.
     *
     * One report per source per run of failures: the same pass sits in the list until the next one
     * replaces it, and re-reporting it on every emission would bury the screen in snackbars.
     */
    private fun reportFailedPasses(passes: List<SourcePass>) {
        val failed = passes.filter { it.hasFailed }

        reportedFailures.retainAll(failed.mapTo(mutableSetOf()) { it.sourceId })

        failed.filterNot { it.sourceId in reportedFailures }.forEach { pass ->
            reportedFailures.add(pass.sourceId)
            errorBus.report(pass.toAppError())
        }
    }

    /** A pass the peer drove counts too - it is the same folder not syncing either way. */
    private val SourcePass.hasFailed: Boolean
        get() = when (this) {
            is SourcePass.Local -> stage == SourcePass.Local.Stage.Failed
            is SourcePass.Remote -> stage == SourcePass.Remote.Stage.Failed
        }

    private fun computeStartDestination(profile: AuthManager.Profile?): Destination =
        when (profile) {
            null -> Destination.Onboarding
            else -> Destination.Files.List
        }

    override fun onCleared() {
        presenceHandover.close()
        runBlocking { lifecycleController.presence.stop() }
    }
}
