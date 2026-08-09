package com.fserver.core.data.detection.connector

import com.fserver.core.domain.model.FoundDevice

internal class DeviceConnectorFactory {
    /**
     * Creates a [DeviceConnector] for provided [FoundDevice].
     */
    fun fromDevice(device: FoundDevice): DeviceConnector = when (val source = device.source) {
        is FoundDevice.Source.NetworkServiceDiscovery -> IpDeviceConnector(
            id = device.id,
            ipAddress = source.ipAddress,
            port = source.port,
        )

        is FoundDevice.Source.SubnetScan -> IpDeviceConnector(
            id = device.id,
            ipAddress = source.ipAddress,
            port = source.port,
        )

        is FoundDevice.Source.ManualEntry -> IpDeviceConnector(
            id = device.id,
            ipAddress = source.ipAddress,
            port = source.port,
        )

        is FoundDevice.Source.NearbyDevice ->
            throw NotImplementedError("NearbyDevice connector is not implemented yet")
    }
}
