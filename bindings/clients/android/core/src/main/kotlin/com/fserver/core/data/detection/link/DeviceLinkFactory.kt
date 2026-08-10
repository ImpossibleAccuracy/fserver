package com.fserver.core.data.detection.link

import com.fserver.core.data.detection.model.DeviceConnectionException
import com.fserver.core.data.repository.NearbyConnectionsRepository
import com.fserver.core.domain.model.FoundDevice

internal class DeviceLinkFactory(
    private val nearbyConnectionsRepository: NearbyConnectionsRepository,
) {
    /**
     * Creates a [DeviceLink] for provided [FoundDevice].
     */
    fun fromDevice(device: FoundDevice): DeviceLink = when (val source = device.source) {
        is FoundDevice.Source.NetworkServiceDiscovery -> IpDeviceLink(
            id = device.id,
            ipAddress = source.ipAddress,
            port = source.port,
        )

        is FoundDevice.Source.SubnetScan -> IpDeviceLink(
            id = device.id,
            ipAddress = source.ipAddress,
            port = source.port,
        )

        is FoundDevice.Source.ManualEntry -> IpDeviceLink(
            id = device.id,
            ipAddress = source.ipAddress,
            port = source.port,
        )

        is FoundDevice.Source.NearbyDevice -> NearbyDeviceLink(
            peer = nearbyConnectionsRepository.findDevice(device.id)
                ?: throw DeviceConnectionException("Device with id ${device.id} not found in Nearby Connections repository")
        )
    }
}
