package com.fserver.app.data.detection.detector

import com.fserver.app.domain.model.FoundDevice

interface DeviceConnector {
    suspend fun loadDeviceInfo(): Result<FoundDevice>
}
