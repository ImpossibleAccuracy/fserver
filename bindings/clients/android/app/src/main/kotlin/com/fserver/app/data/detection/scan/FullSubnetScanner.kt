package com.fserver.app.data.detection.scan

import com.fserver.app.data.detection.detector.DeviceConnector
import com.fserver.app.data.detection.detector.FakeDeviceConnector
import com.fserver.app.domain.model.FoundDevice
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

internal class FullSubnetScanner : DeviceScanner {
    override suspend fun startScan(): Flow<DeviceConnector> = flow {
        emit(
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

        delay(2.seconds)

        emit(
            FakeDeviceConnector(
                loadDelay = 700.milliseconds,
                result = FoundDevice(
                    id = "192.168.1.42:8384",
                    name = "HOME-NAS",
                    kind = FoundDevice.Kind.Nas,
                    access = FoundDevice.Access.Password,
                    source = FoundDevice.Source.SubnetScan("192.168.1.42", 8384),
                ),
            )
        )
    }
}
