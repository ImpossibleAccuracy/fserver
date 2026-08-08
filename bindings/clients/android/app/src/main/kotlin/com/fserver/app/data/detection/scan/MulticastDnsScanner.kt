package com.fserver.app.data.detection.scan

import com.fserver.app.data.detection.connector.DeviceConnector
import com.fserver.app.data.detection.connector.FakeDeviceConnector
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.seconds

internal class MulticastDnsScanner : DeviceScanner {
    override suspend fun startScan(): Flow<DeviceConnector> = flow {
        delay(3.seconds)

        emit(
            FakeDeviceConnector(
                loadDelay = 1.seconds,
                result = null,
            )
        )
    }
}
