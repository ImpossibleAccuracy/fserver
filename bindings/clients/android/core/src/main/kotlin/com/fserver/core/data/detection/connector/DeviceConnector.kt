package com.fserver.core.data.detection.connector

import com.fserver.core.domain.model.DeviceConnectionCapabilities
import com.fserver.core.domain.model.FoundDevice

interface DeviceConnector {
    suspend fun loadDeviceInfo(): Result<FoundDevice>

    suspend fun loadCapabilities(): Result<DeviceConnectionCapabilities>
}
