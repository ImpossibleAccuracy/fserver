package com.fserver.app.presentation.model

import androidx.navigation3.runtime.NavKey
import com.fserver.core.network.info.model.PeerLocator
import kotlinx.serialization.Serializable

sealed interface UnauthenticatedDestinations

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
    data object Onboarding : Destination, UnauthenticatedDestinations

    @Serializable
    data object Files {
        @Serializable
        data object List : Destination

        @Serializable
        data object Picker : Overlay

        /**
         * Where the picked files are sent. [selectionId] names the selection held by
         * `SendSelectionStore`; the entries themselves are unbounded and never travel in the key.
         */
        @Serializable
        data class SendTarget(val selectionId: String) : Destination
    }

    @Serializable
    data object Transfers : Destination

    /**
     * Settings root. It holds no state of its own — every row leads into one of the screens
     * nested here, which is what keeps the root readable as the map of the section.
     */
    @Serializable
    data object Settings : Destination {

        /** Sessions open right now, and the keys trusted to open one without asking again. */
        @Serializable
        data object Devices : Destination

        @Serializable
        data class DeviceDetails(val deviceId: String) : Destination

        /** What this phone accepts from peers, and whether it can be found at all. */
        @Serializable
        data object Security : Destination

        @Serializable
        data object PinChange : Destination

        @Serializable
        data object About : Destination
    }

    /**
     * The fork: every way of reaching a server starts here. Nothing is scanned or requested
     * until the user picks one of them.
     */
    @Serializable
    data object Connect : Destination

    /** Search on the local network — picking methods, granting what they need, and scanning. */
    @Serializable
    data object DeviceDiscovery : Destination

    /**
     * Confirm and connect. Every way of finding a device ends here — the list, the scanner,
     * a typed address — because none of them establishes trust on its own.
     */
    @Serializable
    data class Pairing(
        val peerLocator: PeerLocator,
    ) : Destination

    @Serializable
    data object QrScan : Destination

    @Serializable
    data object ManualAddress : Overlay

    @Serializable
    data object Diagnostics : Destination

}
