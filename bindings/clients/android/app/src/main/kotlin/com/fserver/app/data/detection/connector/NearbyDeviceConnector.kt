package com.fserver.app.data.detection.connector

import com.fserver.app.data.datasource.nearbyconnection.NearbyConnectionsPeer
import com.fserver.app.domain.model.DeviceConnectionCapabilities
import com.fserver.app.domain.model.FoundDevice

class NearbyDeviceConnector(
    val peer: NearbyConnectionsPeer,
) : DeviceConnector {
    override suspend fun loadDeviceInfo(): Result<FoundDevice> = runCatching {
        FoundDevice(
            id = peer.endpointId,
            name = peer.endpointName,
            kind = FoundDevice.Kind.Phone,
            source = FoundDevice.Source.NearbyDevice(
                deviceId = peer.endpointId,
            ),
        )
    }

    override suspend fun loadCapabilities(): Result<DeviceConnectionCapabilities> = runCatching {
        DeviceConnectionCapabilities(
            tlsVersion = DeviceConnectionCapabilities.TLSVersion.TLS_1_2,
            protocolVersion = DeviceConnectionCapabilities.ProtocolVersion.HTTP_2,
            access = DeviceConnectionCapabilities.Access.Open,
            fingerprints = listOf(),
        )
    }
}
