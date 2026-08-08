package com.fserver.app.data.detection.connector

import com.fserver.app.domain.model.FoundDevice

internal class DeviceConnectorFactory {
    /**
     * Creates a [DeviceConnector] for provided [FoundDevice].
     */
    fun fromDevice(device: FoundDevice): DeviceConnector = when (val source = device.source) {
        is FoundDevice.Source.NetworkServiceDiscovery -> IpDeviceConnector(
            ipAddress = source.ipAddress,
            port = source.port,
        )

        is FoundDevice.Source.SubnetScan -> IpDeviceConnector(
            ipAddress = source.ipAddress,
            port = source.port,
        )

        is FoundDevice.Source.ManualEntry -> IpDeviceConnector(
            ipAddress = source.ipAddress,
            port = source.port,
        )

        is FoundDevice.Source.NearbyDevice ->
            throw NotImplementedError("NearbyDevice connector is not implemented yet")
    }
}
