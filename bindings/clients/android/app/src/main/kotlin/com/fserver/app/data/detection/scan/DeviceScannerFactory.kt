package com.fserver.app.data.detection.scan

import com.fserver.app.data.datasource.multicastdns.MulticastDnsDiscoveryService
import com.fserver.app.data.datasource.nearbyconnection.NearbyConnectionsDiscoveryService
import com.fserver.app.domain.model.DetectionMethod
import com.fserver.app.domain.model.DeviceDetectionRequest
import com.fserver.app.domain.model.exception.DetectionFailedException

internal class DeviceScannerFactory(
    private val multicastDnsDiscoveryService: MulticastDnsDiscoveryService,
    private val nearbyConnectionsDiscoveryService: NearbyConnectionsDiscoveryService,
) {
    /**
     * Creates a [DeviceScanner] for provided [DeviceDetectionRequest].
     */
    fun fromRequest(request: DeviceDetectionRequest): DeviceScanner = when (request) {
        is DeviceDetectionRequest.ByMethod -> when (request.method) {
            DetectionMethod.Automatic.NearbyConnections -> NearbyConnectionsScanner(
                connectionsService = nearbyConnectionsDiscoveryService,
            )

            DetectionMethod.Automatic.MulticastDns -> MulticastDnsScanner(
                discoveryService = multicastDnsDiscoveryService,
            )

            DetectionMethod.OnDemand.SubnetScan -> FullSubnetScanner()

            DetectionMethod.OnDemand.ManualAddress ->
                throw DetectionFailedException(
                    UnsupportedOperationException()
                )
        }

        is DeviceDetectionRequest.ByManualAddress -> IpConnectorScanner(
            ipAddress = request.ipAddress,
            port = request.port,
        )

        is DeviceDetectionRequest.QrCode -> QrCodeConnectionScanner(
            payload = request.payload,
        )
    }
}
