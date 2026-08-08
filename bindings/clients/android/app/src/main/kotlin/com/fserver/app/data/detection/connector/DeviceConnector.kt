package com.fserver.app.data.detection.connector

import com.fserver.app.domain.model.DeviceConnectionCapabilities
import com.fserver.app.domain.model.FoundDevice

interface DeviceConnector {
    suspend fun loadDeviceInfo(): Result<FoundDevice>

    suspend fun loadCapabilities(): Result<DeviceConnectionCapabilities>
}
