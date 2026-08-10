package com.fserver.core.data.detection.link

import com.fserver.core.domain.model.DeviceConnectionCapabilities
import com.fserver.core.domain.model.FoundDevice

internal interface DeviceLink {
    suspend fun loadDeviceInfo(): Result<FoundDevice>

    suspend fun loadCapabilities(): Result<DeviceConnectionCapabilities>
}
