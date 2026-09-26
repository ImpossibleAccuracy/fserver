package com.fserver.app.presentation.screens.settings.security.model

/**
 * Two different kinds of switch on one screen, deliberately.
 *
 * [isCodeComparison], [isServerPassword] and [isServerPin] are what peers must satisfy — the server side of the
 * handshake, enforced by `:core`. None of it is a claim about the other end: nothing here restricts
 * what a peer may ask for.
 */
data class SecurityState(
    val isDiscoverable: Boolean = true,
    val isDiscoveryEnabled: Boolean = true,
    val isCodeComparison: Boolean = false,
    val isServerPassword: Boolean = false,
    val isServerPin: Boolean = false,
    /** Raised when the user tried to switch off the only peer method still standing. */
    val showLastMethodWarning: Boolean = false,
)
