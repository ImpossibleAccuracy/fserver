package com.fserver.app.presentation.screens.discovery.model

import com.fserver.app.presentation.model.DeviceUi

data class DeviceDiscoveryState(
    val networkName: String = "",
    val devices: List<DeviceUi> = emptyList(),
    val searching: Boolean = true,
)