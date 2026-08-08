package com.fserver.app.data.detection.connector

import com.fserver.app.data.detection.model.DeviceConnectionException
import com.fserver.app.domain.Constants
import com.fserver.app.domain.model.DeviceConnectionCapabilities
import com.fserver.app.domain.model.FoundDevice
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

private const val REACHABLE_HOST_PREFIX = "192.168."

internal class IpDeviceConnector(
    val ipAddress: String,
    val port: Int?,
) : DeviceConnector {
    override suspend fun loadDeviceInfo(): Result<FoundDevice> = runCatching {
        delay(1.5.seconds)

        // Debug filtration, remove after implementing real connection logic. This is to simulate unreachable hosts.
        if (!ipAddress.startsWith(REACHABLE_HOST_PREFIX))
            throw DeviceConnectionException("Unreachable host: $ipAddress")

        val resolvedPort = port ?: Constants.DEFAULT_PORT

        FoundDevice(
            id = "$ipAddress:$resolvedPort",
            name = ipAddress,
            kind = FoundDevice.Kind.Unknown,
            source = FoundDevice.Source.ManualEntry(ipAddress, resolvedPort),
        )
    }

    override suspend fun loadCapabilities(): Result<DeviceConnectionCapabilities> = runCatching {
        delay(3.seconds)

        DeviceConnectionCapabilities(
            tlsVersion = DeviceConnectionCapabilities.TLSVersion.TLS_1_2,
            protocolVersion = DeviceConnectionCapabilities.ProtocolVersion.HTTP_2,
            access = DeviceConnectionCapabilities.Access.Password,
            fingerprints = listOf(
                DeviceConnectionCapabilities.Fingerprint("9f2c 4a01"),
                DeviceConnectionCapabilities.Fingerprint("b7d3 e820"),
                DeviceConnectionCapabilities.Fingerprint("15aa cc94"),
                DeviceConnectionCapabilities.Fingerprint("0f6b 7e311"),
            )
        )
    }
}
