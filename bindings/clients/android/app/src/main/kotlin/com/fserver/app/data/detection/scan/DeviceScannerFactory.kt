package com.fserver.app.data.detection.scan

import com.fserver.app.data.detection.scan.multicast.MulticastDnsDiscoveryService
import com.fserver.app.data.detection.scan.multicast.MulticastDnsScanner
import com.fserver.app.domain.model.DetectionMethod
import com.fserver.app.domain.model.DeviceDetectionRequest
import com.fserver.app.domain.model.exception.DetectionFailedException

internal class DeviceScannerFactory(
    private val multicastDnsDiscoveryService: MulticastDnsDiscoveryService,
) {
    /**
     * Creates a [DeviceScanner] for provided [DeviceDetectionRequest].
     */
    fun fromRequest(request: DeviceDetectionRequest): DeviceScanner = when (request) {
        is DeviceDetectionRequest.ByMethod -> when (request.method) {
            DetectionMethod.Automatic.DeviceDiscoveryApi -> DeviceDiscoveryApiScanner()

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
