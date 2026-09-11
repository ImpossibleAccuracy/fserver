package com.fserver.app.presentation.screens.source.setup.target.model

import androidx.compose.runtime.Immutable
import com.fserver.core.network.device.model.DeviceKind

@Immutable
data class SourceTargetState(
    val connected: List<DeviceUi> = emptyList(),
    val known: List<DeviceUi> = emptyList(),
    val discovered: List<DeviceUi> = emptyList(),
    val selectedDeviceId: String? = null,
    val isSearching: Boolean = false,
    val isScanningSubnet: Boolean = false,
) {
    val canContinue: Boolean
        get() = selectedDeviceId != null

    @Immutable
    data class DeviceUi(
        val id: String,
        val name: String,
        val kind: DeviceKind?,
        val address: String?,
        val isBusy: Boolean = false,
    )

    companion object {
        val SampleConnected = listOf(
            DeviceUi(
                id = "home-nas",
                name = "HOME-NAS",
                kind = DeviceKind.Nas,
                address = "192.168.1.42:8384",
            ),
            DeviceUi(
                id = "work-laptop",
                name = "WORK-LAPTOP",
                kind = DeviceKind.Laptop,
                address = "192.168.1.17:8384",
            ),
        )

        val SampleKnown = listOf(
            DeviceUi(
                id = "studio-pc",
                name = "STUDIO-PC",
                kind = DeviceKind.Desktop,
                address = "192.168.1.9:8384",
            ),
        )

        val SampleDiscovered = listOf(
            DeviceUi(
                id = "macbook",
                name = "MacBook-Pro.local",
                kind = DeviceKind.Laptop,
                address = "192.168.1.14:8384",
            ),
        )
    }
}
