package com.fserver.app.data.detection.scan

import com.fserver.app.data.detection.detector.DeviceConnector
import com.fserver.app.data.detection.detector.FakeDeviceConnector
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Duration.Companion.seconds

internal class MulticastDnsScanner : DeviceScanner {
    override suspend fun startScan(): Flow<DeviceConnector> = flowOf(
        FakeDeviceConnector(
            loadDelay = 4.seconds,
            result = null,
        ),
    )
}
