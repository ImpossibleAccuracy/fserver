package com.fserver.app.data.detection.detector

import com.fserver.app.data.detection.model.DeviceConnectionException
import com.fserver.app.domain.model.FoundDevice
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
}
