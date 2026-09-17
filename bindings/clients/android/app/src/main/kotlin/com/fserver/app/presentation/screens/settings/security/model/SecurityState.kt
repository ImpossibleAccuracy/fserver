package com.fserver.app.presentation.screens.settings.security.model

/**
 * Two different kinds of switch on one screen, deliberately.
 *
 * [isCodeComparison] and [isServerPassword] are what peers must satisfy — the server side of the
 * handshake, enforced by `:core`. The rest describe an app lock on this phone. Neither group is a
 * claim about the other end: nothing here restricts what a peer may ask for.
 */
data class SecurityState(
    val isDiscoverable: Boolean = true,
    val isDiscoveryEnabled: Boolean = true,
    val deviceName: String = "",
    val isCodeComparison: Boolean = false,
    val isServerPassword: Boolean = false,
    val isPinEnabled: Boolean = false,
    val isBiometricUnlock: Boolean = false,
    val isQrConnect: Boolean = true,
    /** Raised when the user tried to switch off the only peer method still standing. */
    val showLastMethodWarning: Boolean = false,
)
