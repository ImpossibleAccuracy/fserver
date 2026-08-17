package com.fserver.app.presentation.screens.settings.security

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.data.AppSettingsStore
import com.fserver.app.presentation.screens.settings.security.model.SecurityIntent
import com.fserver.app.presentation.screens.settings.security.model.SecurityState
import com.fserver.core.network.auth.AuthMethod
import com.fserver.core.network.auth.OfferedAuthMethod
import com.fserver.core.store.AuthSettingsStore
import com.fserver.core.store.DeviceIdentityStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Visibility and confirmation methods.
 *
 * The peer-facing methods live in [AuthSettingsStore], which `:core` watches, so a toggle here
 * reaches the running node without a restart. Everything else is a local preference — see
 * [AppSettingsStore] for what is still only remembered.
 */
class SecurityViewModel(
    private val appSettings: AppSettingsStore,
    private val authSettings: AuthSettingsStore,
    private val identityStore: DeviceIdentityStore,
) : ViewModel() {

    private val deviceName = MutableStateFlow("")
    private val lastMethodWarning = MutableStateFlow(false)

    val state: StateFlow<SecurityState> = combine(
        authSettings.offeredMethods,
        appSettings.discoverable,
        combine(
            appSettings.pinEnabled,
            appSettings.biometricUnlock,
            appSettings.qrConnect,
            ::Triple,
        ),
        deviceName,
        lastMethodWarning,
    ) { offered, discoverable, (pin, biometric, qr), name, warning ->
        SecurityState(
            isDiscoverable = discoverable,
            deviceName = name,
            isCodeComparison = offered.any { it is OfferedAuthMethod.ConfirmFingerprint },
            isServerPassword = offered.any { it is OfferedAuthMethod.Password },
            isPinEnabled = pin,
            isBiometricUnlock = biometric,
            isQrConnect = qr,
            showLastMethodWarning = warning,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SecurityState(),
    )

    init {
        viewModelScope.launch { deviceName.value = identityStore.localDevice.load().displayName }
    }

    fun onIntent(intent: SecurityIntent) {
        when (intent) {
            is SecurityIntent.DiscoverableChanged ->
                launchUpdate { appSettings.setDiscoverable(intent.enabled) }

            is SecurityIntent.CodeComparisonChanged ->
                setMethod(AuthMethod.ConfirmFingerprint, intent.enabled)

            is SecurityIntent.ServerPasswordChanged ->
                setMethod(AuthMethod.Password, intent.enabled)

            is SecurityIntent.PinChanged ->
                launchUpdate { appSettings.setPinEnabled(intent.enabled) }

            is SecurityIntent.BiometricChanged ->
                launchUpdate { appSettings.setBiometricUnlock(intent.enabled) }

            is SecurityIntent.QrConnectChanged ->
                launchUpdate { appSettings.setQrConnect(intent.enabled) }

            is SecurityIntent.ServerPasswordSet -> launchUpdate {
                // Setting a password is an explicit act, so it also opens the method: storing a
                // secret nothing would ever ask for is the more surprising outcome.
                authSettings.setServerPassword(intent.password)
                authSettings.setEnabled(AuthMethod.Password, enabled = true)
            }

            is SecurityIntent.DeviceRenamed -> launchUpdate {
                identityStore.setDisplayName(intent.name)
                deviceName.value = identityStore.localDevice.load().displayName
            }

            SecurityIntent.WarningDismissed -> lastMethodWarning.value = false
        }
    }

    /**
     * Refuses to close the last open door. A phone that offers no method cannot be connected to at
     * all, and the screen that got it there is the wrong place to discover that.
     */
    private fun setMethod(method: AuthMethod, enabled: Boolean) {
        if (!enabled && authSettings.offeredMethods.value.size <= 1) {
            lastMethodWarning.value = true
            return
        }

        launchUpdate { authSettings.setEnabled(method, enabled) }
    }

    private fun launchUpdate(block: suspend () -> Unit) {
        lastMethodWarning.value = false
        viewModelScope.launch { block() }
    }
}
