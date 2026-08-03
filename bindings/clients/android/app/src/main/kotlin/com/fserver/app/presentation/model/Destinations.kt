package com.fserver.app.presentation.model

import androidx.navigation3.runtime.NavKey
import kotlinx.serialization.Serializable

/**
 * Navigation 3 keys. Each key is the whole description of a destination — the back stack
 * is a plain list of these, owned by the app rather than by a navigator.
 *
 * They are `@Serializable` so `rememberNavBackStack` can restore the stack across process
 * death.
 */
@Serializable
sealed interface Destination : NavKey {

    /** The connection flow: everything before the user has a server to browse. */
    @Serializable
    data object Onboarding : Destination

    // Top-level
    @Serializable
    data object Files : Destination

    @Serializable
    data object Transfers : Destination

    @Serializable
    data object Settings : Destination

    // Other screens

    @Serializable
    data object DeviceDiscovery : Destination

    @Serializable
    data class Pairing(val deviceId: String) : Destination

    @Serializable
    data object QrScan : Destination

    @Serializable
    data object ServerProfile : Destination

    @Serializable
    data object Diagnostics : Destination

}
