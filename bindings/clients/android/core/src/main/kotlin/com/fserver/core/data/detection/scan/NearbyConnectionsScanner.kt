package com.fserver.core.data.detection.scan

import com.fserver.core.data.datasource.nearbyconnection.NearbyConnectionsEvent
import com.fserver.core.data.detection.connector.NearbyDeviceConnector
import com.fserver.core.data.detection.model.DeviceScanningException
import com.fserver.core.data.repository.NearbyConnectionsRepository
import com.fserver.core.data.utils.InternalConnectionApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

internal class NearbyConnectionsScanner(
    private val repository: NearbyConnectionsRepository,
) : DeviceScanner {
    @OptIn(InternalConnectionApi::class)
    override fun startScan(): Flow<DeviceScanEvent> {
        repository.startBoth()

        return repository.events
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

                    is NearbyConnectionsEvent.Connected -> null

                    is NearbyConnectionsEvent.Message -> null

                    is NearbyConnectionsEvent.Disconnected -> DeviceScanEvent.Lost(event.id)

                    // Peer is gone, drop it
                    is NearbyConnectionsEvent.PeerError -> DeviceScanEvent.Lost(event.id)
                }
            }
    }
}
