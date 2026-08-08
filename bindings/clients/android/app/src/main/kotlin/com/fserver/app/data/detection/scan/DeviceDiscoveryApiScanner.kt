package com.fserver.app.data.detection.scan

import com.fserver.app.data.detection.connector.FakeDeviceConnector
import com.fserver.app.domain.model.FoundDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.seconds

internal class DeviceDiscoveryApiScanner : DeviceScanner {
    override suspend fun startScan(): Flow<DeviceScanEvent> = flow {
        delay(2.seconds)

        emit(
            DeviceScanEvent.Found(
                FakeDeviceConnector(
                    loadDelay = 1.seconds,
                    result = FoundDevice(
                        id = "192.168.1.14:8384",
                        name = "MacBook-Pro.local",
                        kind = FoundDevice.Kind.Laptop,
                        source = FoundDevice.Source.SubnetScan("192.168.1.14", 8384),
                    ),
                )
            )
        )
    }
}
