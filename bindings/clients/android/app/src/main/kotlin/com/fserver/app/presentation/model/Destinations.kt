package com.fserver.app.presentation.model

import androidx.navigation3.runtime.NavKey
import com.fserver.app.presentation.screens.source.setup.shared.model.SourceAccessUi
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
        /** The content feed: every device in a row, everything they hold in one list. */
        @Serializable
        data object List : Destination

        /** One folder of the feed */
        @Serializable
        data class Folder(
            val folderPath: String,
        ) : Destination

        /**
         * The fork behind the "+" button: open someone else's files, or share your own. Both
         * ways out are the same two the empty file list offers.
         */
        @Serializable
        data object Actions : Overlay

        /** What can be done to one registered source: rename it, or stop syncing it. */
        @Serializable
        data class SourceActions(val sourceId: String) : Overlay
    }

    /**
     * Everything that happens to a source, both halves of it.
     *
     * [Setup] is this device asking a peer to host one of its folders; [Request] is answering the
     * same ask from the other side. They converge on [Progress] and [Done], which are keyed by the
     * source id alone and so read the same whichever half opened them.
     */
    @Serializable
    data object Source {

        /**
         * Registering a source here: what the app may see on this phone, what to do with it, and
         * where it goes. The answers build up in a ViewModel scoped to [Setup.Pick], so these keys
         * carry only what a screen needs to draw itself.
         */
        @Serializable
        data object Setup {

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

            /** Which connected device receives everything the source will produce. */
            @Serializable
            data object Target : Destination

            /** Whatever the chosen mode still needs to know, then the work before it is on. */
            @Serializable
            data class Conditions(val kind: SourceKindUi, val mode: SourceModeUi) : Destination
        }

        /**
         * Answering a peer's ask to host one of its sources here.
         *
         * Every key carries the source id rather than the request itself: the ask lives in the
         * engine until it is answered, so each screen re-reads it and the flow survives process
         * death without a shared ViewModel behind it.
         */
        @Serializable
        data object Request {

            /** Everything parked, one card per decision. Both places that nag about a request lead here. */
            @Serializable
            data object List : Destination

            /** Who asked, for what, and under which mode. */
            @Serializable
            data class Details(val sourceId: String) : Destination

            /** Where the files this device takes on will be written. */
            @Serializable
            data class Location(val sourceId: String) : Destination
        }

        /**
         * Registered, and waiting: first on the peer, then on the first pass. Neither is the
         * user's to drive, so this screen only offers to leave.
         */
        @Serializable
        data class Progress(val sourceId: String) : Destination

        /** What was just turned on, in four lines. */
        @Serializable
        data class Done(val sourceId: String) : Destination
    }

    /**
     * What is moving now and what already happened, in one stream. Transfers are not a place of
     * their own: a finished upload and an offload pass are the same kind of news.
     */
    @Serializable
    data object Activity : Destination

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
