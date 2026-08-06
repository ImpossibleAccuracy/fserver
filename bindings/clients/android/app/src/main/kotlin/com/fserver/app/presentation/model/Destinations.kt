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
    /**
     * A destination drawn *on top of* the entry below it (bottom sheet, dialog) rather than
     * replacing it. The screen underneath stays visible, so app chrome — the bottom bar — keeps
     * behaving as if that screen were still the current one.
     */
    @Serializable
    sealed interface Overlay : Destination


    @Serializable
    data object Onboarding : Destination

    @Serializable
    data object Files {
        @Serializable
        data object List : Destination

        @Serializable
        data object Picker : Overlay
    }

    @Serializable
    data object Transfers : Destination

    @Serializable
    data object Settings : Destination

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
