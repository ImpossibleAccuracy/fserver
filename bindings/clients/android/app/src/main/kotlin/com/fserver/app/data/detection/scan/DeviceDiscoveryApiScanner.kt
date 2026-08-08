package com.fserver.app.data.detection.scan

import com.fserver.app.data.detection.detector.DeviceConnector
import com.fserver.app.data.detection.detector.FakeDeviceConnector
import com.fserver.app.domain.model.FoundDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlin.time.Duration.Companion.seconds

internal class DeviceDiscoveryApiScanner : DeviceScanner {
    override suspend fun startScan(): Flow<DeviceConnector> {
        return flowOf(
            FakeDeviceConnector(
                loadDelay = 1.seconds,
                result = FoundDevice(
                    id = "192.168.1.14:8384",
                    name = "MacBook-Pro.local",
                    kind = FoundDevice.Kind.Laptop,
                    access = FoundDevice.Access.Open,
                    source = FoundDevice.Source.SubnetScan("192.168.1.14", 8384),
                ),
            )
        )
    }
}
