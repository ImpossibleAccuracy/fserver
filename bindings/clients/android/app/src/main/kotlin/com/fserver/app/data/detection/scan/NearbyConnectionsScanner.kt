package com.fserver.app.data.detection.scan

import com.fserver.app.data.datasource.nearbyconnection.NearbyConnectionsEvent
import com.fserver.app.data.datasource.nearbyconnection.NearbyConnectionsDiscoveryService
import com.fserver.app.data.detection.connector.NearbyDeviceConnector
import com.fserver.app.data.detection.model.DeviceScanningException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

internal class NearbyConnectionsScanner(
    private val connectionsService: NearbyConnectionsDiscoveryService,
) : DeviceScanner {
    override fun startScan(): Flow<DeviceScanEvent> = connectionsService.start()
        .mapNotNull { event ->
            when (event) {
                NearbyConnectionsEvent.Idle -> null
                is NearbyConnectionsEvent.Error -> throw DeviceScanningException(
                    message = "Nearby Connections error: ${event.e.message}",
                    cause = event.e,
                )

                is NearbyConnectionsEvent.Found -> DeviceScanEvent.Found(
                    NearbyDeviceConnector(peer = event.peer)
                )

                is NearbyConnectionsEvent.Message -> null

                is NearbyConnectionsEvent.Disconnected -> DeviceScanEvent.Lost(event.id)

                // Peer is gone, drop it
                is NearbyConnectionsEvent.PeerError -> DeviceScanEvent.Lost(event.id)
            }
        }
}
