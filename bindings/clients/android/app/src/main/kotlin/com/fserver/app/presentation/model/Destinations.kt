package com.fserver.app.presentation.model

import androidx.navigation3.runtime.NavKey
import com.fserver.app.presentation.screens.source.shared.model.SourceAccessUi
import com.fserver.app.presentation.screens.source.shared.model.SourceKindUi
import com.fserver.app.presentation.screens.source.shared.model.SourceModeUi
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

        /**
         * The fork behind the "+" button: open someone else's files, or share your own. Both
         * ways out are the same two the empty file list offers.
         */
        @Serializable
        data object Actions : Overlay
    }

    /** Which connected device receives everything the source being configured will produce. */
    @Serializable
    data object TargetDevice : Destination

    /**
     * Setting up a source: what the app may see on this phone, what to do with it, and where it
     * goes. Every screen carries the whole answer so far, so the flow has no state of its own
     * between them.
     */
    @Serializable
    data object Source {

        /** Screen 0 — the one question the flow starts with. */
        @Serializable
        data object Pick : Destination

        /** Explains the branch's access, asks the system for it, and reports the outcome. */
        @Serializable
        data class Access(val kind: SourceKindUi) : Destination

        /** One mode per source, once access is in hand. */
        @Serializable
        data class Mode(
            val kind: SourceKindUi,
            val access: SourceAccessUi = SourceAccessUi.Full,
        ) : Destination

        /** Whatever the chosen mode still needs to know, then the work before it is on. */
        @Serializable
        data class Conditions(val kind: SourceKindUi, val mode: SourceModeUi) : Destination

        /**
         * Registered, and waiting: first on the peer to take on its half of the source, then on
         * the first pass. Neither is the user's to drive, so this screen only offers to leave.
         */
        @Serializable
        data object Upload : Destination

        /** What was just turned on, in four lines. */
        @Serializable
        data object Done : Destination
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
