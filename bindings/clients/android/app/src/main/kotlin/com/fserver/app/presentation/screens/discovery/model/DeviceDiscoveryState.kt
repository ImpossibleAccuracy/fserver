package com.fserver.app.presentation.screens.discovery.model

import androidx.compose.runtime.Immutable

data class DeviceDiscoveryState(
    val network: NetworkInfo? = null,
    val devices: List<Device> = emptyList(),
    val searching: Boolean = true,
) {
    data class NetworkInfo(
        val name: String,
    )

    @Immutable
    data class Device(
        val id: String,
        val name: String,
        val address: String,
        val online: Boolean,
        val lastSeenLabel: String? = null,
    )

    companion object {
        val SampleDevices = listOf(
            Device(
                id = "macbook",
                name = "MacBook-Pro.local",
                address = "192.168.1.14:8384",
                online = true,
            ),
            Device(
                id = "nas",
                name = "HOME-NAS",
                address = "nas.local:8384",
                online = false,
                lastSeenLabel = "2 h",
            ),
        )
    }
}
