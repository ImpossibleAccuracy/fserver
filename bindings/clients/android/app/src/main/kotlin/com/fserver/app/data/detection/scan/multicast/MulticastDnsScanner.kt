package com.fserver.app.data.detection.scan.multicast

import com.fserver.app.data.detection.connector.IpDeviceConnector
import com.fserver.app.data.detection.model.DeviceScanningException
import com.fserver.app.data.detection.scan.DeviceScanEvent
import com.fserver.app.data.detection.scan.DeviceScanner
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow

internal class MulticastDnsScanner(
    private val discoveryService: MulticastDnsDiscoveryService,
) : DeviceScanner {
    override suspend fun startScan(): Flow<DeviceScanEvent> = channelFlow {
        discoveryService.start().collect {
            when (it) {
                is MulticastDnsEvent.Error -> close(
                    DeviceScanningException(
                        message = "Error during mDNS scan: ${it.errorCode}",
                    )
                )

                MulticastDnsEvent.Idle -> {
                    // No-op
                }

                MulticastDnsEvent.Scanning -> {
                    // No-op
                }

                MulticastDnsEvent.Closed -> close()

                is MulticastDnsEvent.Found -> send(
                    DeviceScanEvent.Found(
                        IpDeviceConnector(
                            ipAddress = it.peer.host,
                            port = it.peer.port,
                            id = it.peer.id,
                        )
                    )
                )

                is MulticastDnsEvent.Disconnected -> send(DeviceScanEvent.Lost(it.id))

                // Peer is gone, drop it
                is MulticastDnsEvent.PeerError -> send(DeviceScanEvent.Lost(it.id))
            }
        }
    }
}
