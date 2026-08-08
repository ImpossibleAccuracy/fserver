package com.fserver.app.data.detection.scan

import kotlinx.coroutines.flow.Flow

internal interface DeviceScanner {
    /**
     * Runs the scan, reporting peers as they appear and - for methods that keep watching -
     * as they go away again.
     */
    suspend fun startScan(): Flow<DeviceScanEvent>
}
