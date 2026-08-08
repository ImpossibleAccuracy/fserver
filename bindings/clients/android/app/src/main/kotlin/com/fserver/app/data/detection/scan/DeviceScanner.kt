package com.fserver.app.data.detection.scan

import com.fserver.app.data.detection.detector.DeviceConnector
import kotlinx.coroutines.flow.Flow

internal interface DeviceScanner {
    suspend fun startScan(): Flow<DeviceConnector>
}
