package com.fserver.app.data.detection.scan

import com.fserver.app.data.datasource.multicastdns.MulticastDnsDiscoveryService
import com.fserver.app.data.datasource.multicastdns.MulticastDnsEvent
import com.fserver.app.data.detection.connector.IpDeviceConnector
import com.fserver.app.data.detection.model.DeviceScanningException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

internal class MulticastDnsScanner(
    private val discoveryService: MulticastDnsDiscoveryService,
) : DeviceScanner {
    override fun startScan(): Flow<DeviceScanEvent> = discoveryService.start()
        .mapNotNull { event ->
            when (event) {
                is MulticastDnsEvent.Error -> throw DeviceScanningException(
                    message = "Error during mDNS scan: ${event.errorCode}",
                )

                MulticastDnsEvent.Idle,
                MulticastDnsEvent.Scanning,
                MulticastDnsEvent.Closed -> null

                is MulticastDnsEvent.Found -> DeviceScanEvent.Found(
                    IpDeviceConnector(
                        ipAddress = event.peer.host,
                        port = event.peer.port,
                        id = event.peer.id,
                    )
                )

                is MulticastDnsEvent.Disconnected -> DeviceScanEvent.Lost(event.id)

                // Peer is gone, drop it
                is MulticastDnsEvent.PeerError -> DeviceScanEvent.Lost(event.id)
            }
        }
}
