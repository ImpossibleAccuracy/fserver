package com.fserver.core.data.detection.connector

import com.fserver.core.data.detection.model.DeviceConnectionException
import com.fserver.core.domain.model.DeviceConnectionCapabilities
import com.fserver.core.domain.model.FoundDevice
import kotlinx.coroutines.delay
import kotlin.time.Duration

internal class FakeDeviceConnector(
    val loadDelay: Duration,
    val result: FoundDevice?,
) : DeviceConnector {
    override suspend fun loadDeviceInfo(): Result<FoundDevice> = runCatching {
        delay(loadDelay)

        if (result == null) {
            throw DeviceConnectionException("Fake device connector error")
        }

        return@runCatching result
    }

    override suspend fun loadCapabilities(): Result<DeviceConnectionCapabilities> {
        return Result.failure(
            DeviceConnectionException("Fake device connector does not support loading capabilities")
        )
    }
}
