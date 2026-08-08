package com.fserver.app.presentation.screens.discovery.model

import androidx.compose.runtime.Immutable
import com.fserver.app.domain.model.DetectionMethod

@Immutable
data class DeviceDiscoveryState(
    val network: NetworkInfoUi? = null,
    val devices: List<DeviceUi> = emptyList(),
    val detectionMethods: List<DetectionMethodUi> = emptyList(),
) {
    val isSearching: Boolean
        get() = detectionMethods.any { it.isSearching }

    data class NetworkInfoUi(
        val name: String,
        val type: Type,
    ) {
        enum class Type {
            WiFi,
            Mobile,
        }
    }

    data class DetectionMethodUi(
        val method: DetectionMethod,
        val isSearching: Boolean,
    )

    @Immutable
    data class DeviceUi(
        val id: String,
        val name: String,
        val address: String,
        val online: Boolean,
        val lastSeenLabel: String? = null,
    )

    companion object {
        val SampleDevices = listOf(
            DeviceUi(
                id = "macbook",
                name = "MacBook-Pro.local",
                address = "192.168.1.14:8384",
                online = true,
            ),
            DeviceUi(
                id = "nas",
                name = "HOME-NAS",
                address = "nas.local:8384",
                online = false,
                lastSeenLabel = "2 h",
            ),
        )
    }
}
