package com.fserver.app.presentation.screens.discovery.automatic.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.screens.discovery.shared.NetworkCardUi
import com.fserver.app.presentation.composable.model.RequirementRowUi
import com.fserver.app.presentation.permission.RequirementAction
import com.fserver.core.network.device.model.DeviceKind
import com.fserver.core.network.TransportKind

@Immutable
data class DeviceDiscoveryState(
    val network: NetworkCardUi? = null,
    /** What clears the way to naming the network; null when nothing is in the way. */
    val networkAction: RequirementAction? = null,
    val methods: List<MethodUi> = emptyList(),
    val devices: List<DeviceUi> = emptyList(),
    /**
     * The user has started a search on this screen.
     *
     * Sticky: methods finishing does not put the screen back to picking them, and neither does
     * Stop. What was found stays on screen, because that is the answer the user asked for.
     */
    val isSearching: Boolean = false,
    /** Non-null while the permissions sheet for one method is open. */
    val methodSetup: MethodSetupUi? = null,
) {
    val selectedCount: Int get() = methods.count { it.selected }
    val runningCount: Int get() = methods.count { it.isScanning }

    /**
     * One search method as the list draws it.
     *
     * `@Immutable` is load-bearing: [method] is a sealed interface from `:core`, which Compose
     * cannot infer stability for, and without the annotation every found device would recompose
     * the whole method list.
     */
    @Immutable
    data class MethodUi(
        val method: TransportKind,
        val selected: Boolean,
        val isScanning: Boolean,
        /**
         * Started at least once in this search. A method that has finished is still part of
         * the search — it is offered a retry, not greyed out as if it had never taken part.
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

    /** Screen 03: what one method is still waiting on. */
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

    @Immutable
    data class DeviceUi(
        val id: String,
        val name: String,
        val kind: DeviceKind?,
        val address: String,
        val isPaired: Boolean,
    )

    companion object {
        val SampleDevices = listOf(
            DeviceUi(
                id = "macbook",
                name = "MacBook-Pro.local",
                kind = DeviceKind.Laptop,
                address = "192.168.1.14:8384",
                isPaired = false,
            ),
            DeviceUi(
                id = "nas",
                name = "HOME-NAS",
                kind = DeviceKind.Nas,
                address = "nas.local:8384",
                isPaired = false,
            ),
        )
    }
}
