package com.fserver.app.presentation.screens.discovery.connect.model

import com.fserver.app.presentation.composable.model.PeerUi
import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.composable.model.RequirementRowUi
import com.fserver.app.presentation.permission.RequirementAction
import com.fserver.app.presentation.screens.discovery.shared.NetworkCardUi
import com.fserver.core.network.TransportKind
import com.fserver.core.network.device.model.DeviceKind

@Immutable
data class ConnectState(
    val network: NetworkCardUi? = null,
    /** What clears the way to naming the network; null when nothing is in the way. */
    val networkAction: RequirementAction? = null,
    /**
     * Devices the user has met before — connected first, then trusted but offline. One list,
     * because "my devices" is how the user reads them; the tag says which are reachable now.
     */
    val known: List<DeviceUi> = emptyList(),
    /** Heard over a scan and never trusted: reaching one costs a code comparison. */
    val discovered: List<DeviceUi> = emptyList(),
    val methods: List<MethodUi> = emptyList(),
    /** True while the search-methods sheet is open. */
    val isMethodsOpen: Boolean = false,
    /** Non-null while the permissions sheet for one method is open, above the methods sheet. */
    val methodSetup: MethodSetupUi? = null,
) {
    val runningCount: Int get() = methods.count { it.isScanning }
    val isScanning: Boolean get() = runningCount > 0
    val isEmpty: Boolean get() = known.isEmpty() && discovered.isEmpty()

    @Immutable
    data class DeviceUi(
        val peer: PeerUi,
        val address: String?,
        val isBusy: Boolean = false,
    ) {
        val id: String get() = peer.id

        /** A session is up: picking it is the whole interaction, nothing to dial. */
        val isConnected: Boolean get() = peer.online
    }

    /**
     * One search method as the sheet draws it.
     *
     * `@Immutable` is load-bearing: [method] is an enum from `:core` that Compose cannot infer
     * stability for, and without it every found device would recompose the whole method list.
     */
    @Immutable
    data class MethodUi(
        val method: TransportKind,
        val isScanning: Boolean,
        /**
         * Started at least once while this screen was open, so [foundCount] is what that run
         * turned up rather than a count of nothing.
         */
        val hasRun: Boolean,
        val foundCount: Int,
        /** Unmet requirements; `null` until the first check comes back. */
        val unmetCount: Int?,
        /** Something no button can fix — absent hardware, a network that cannot carry this. */
        val isBlocked: Boolean,
    ) {
        val isReady: Boolean get() = unmetCount == 0
    }

    /** What one method is still waiting on. */
    @Immutable
    data class MethodSetupUi(
        val method: TransportKind,
        val solvable: List<RequirementRowUi>,
        val blockers: List<RequirementRowUi>,
    ) {
        val unmetCount: Int get() = solvable.size + blockers.size

        /** What "grant the rest" runs first. Null once nothing solvable is left. */
        val firstAction: RequirementAction? get() = solvable.firstNotNullOfOrNull { it.action }
    }

    companion object {
        val SampleKnown = listOf(
            DeviceUi(
                peer = PeerUi("home-nas", "HOME-NAS", DeviceKind.Nas, online = true),
                address = "192.168.1.42:8384",
            ),
            DeviceUi(
                peer = PeerUi("work-laptop", "WORK-LAPTOP", DeviceKind.Laptop, online = true),
                address = "192.168.1.17:8384",
            ),
            DeviceUi(
                peer = PeerUi("studio-pc", "STUDIO-PC", DeviceKind.Desktop),
                address = null,
            ),
        )

        val SampleDiscovered = listOf(
            DeviceUi(
                peer = PeerUi("macbook", "MacBook-Pro.local", DeviceKind.Laptop),
                address = "192.168.1.14:8384",
            ),
        )
    }
}
