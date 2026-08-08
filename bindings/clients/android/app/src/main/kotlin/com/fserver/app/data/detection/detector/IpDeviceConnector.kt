package com.fserver.app.data.detection.detector

import com.fserver.app.data.detection.model.DeviceConnectionException
import com.fserver.app.domain.model.FoundDevice
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

private const val REACHABLE_HOST_PREFIX = "192.168."
private const val SAMPLE_DEFAULT_PORT = 8080

internal class IpDeviceConnector(
    val ipAddress: String,
    val port: Int?,
) : DeviceConnector {
    override suspend fun loadDeviceInfo(): Result<FoundDevice> = runCatching {
        delay(1.5.seconds)

        if (!ipAddress.startsWith(REACHABLE_HOST_PREFIX))
            throw DeviceConnectionException("Unreachable host: $ipAddress")

        val resolvedPort = port ?: SAMPLE_DEFAULT_PORT

        FoundDevice(
            id = "$ipAddress:$resolvedPort",
            name = ipAddress,
            kind = FoundDevice.Kind.Unknown,
            // An address alone says nothing about what guards the far end; the fixture
            // picks the demanding case so the connect screen's password branch is real.
            access = FoundDevice.Access.Password,
            source = FoundDevice.Source.ManualEntry(ipAddress, resolvedPort),
        )
    }
}
