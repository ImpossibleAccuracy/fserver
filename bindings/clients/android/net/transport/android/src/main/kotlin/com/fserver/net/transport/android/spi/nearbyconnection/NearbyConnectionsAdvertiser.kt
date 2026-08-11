package com.fserver.net.transport.android.spi.nearbyconnection

import com.fserver.net.spi.Advertiser
import com.fserver.net.spi.SpiId
import com.fserver.net.transport.android.datasource.nearbyconnection.NCAdvertiserEvent
import com.fserver.net.transport.android.datasource.nearbyconnection.NearbyConnectionsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

internal class NearbyConnectionsAdvertiser(
    private val repository: NearbyConnectionsRepository,
) : Advertiser {
    override val id: SpiId = NearbyConnectionsSPI.ID

    override fun advertise(payload: Advertiser.Payload): Flow<Advertiser.Event> =
        // TODO: payload.attributes ignored, fix
        repository
            .startAdvertising(payload.identity)
            .mapNotNull { event ->
                when (event) {
                    NCAdvertiserEvent.Registered -> Advertiser.Event.Started
                    is NCAdvertiserEvent.Error -> Advertiser.Event.Failed(event.error)
                    NCAdvertiserEvent.Closed -> Advertiser.Event.Stopped

                    else -> null
                }
            }
}
