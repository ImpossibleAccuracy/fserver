package com.fserver.net.transport.android.spi.nearbyconnection

import com.fserver.net.spi.Advertiser
import com.fserver.net.spi.SpiId
import com.fserver.net.transport.android.datasource.nearbyconnection.NCAdvertiserEvent
import com.fserver.net.transport.android.datasource.nearbyconnection.NearbyConnectionsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onCompletion

internal class NearbyConnectionsAdvertiser(
    private val repository: NearbyConnectionsRepository,
) : Advertiser {
    override val id: SpiId = NearbyConnectionsSPI.ID

    /**
     * The advertisement is packed into the endpoint info - 131 bytes, and the only thing Nearby
     * carries about an endpoint before it is connected. [Advertiser.Payload.essential] is spent
     * first; [Advertiser.Payload.optional] goes out only while there is room left, and what does
     * not fit a peer learns from the handshake instead.
     */
    override suspend fun advertise(payload: Advertiser.Payload): Flow<Advertiser.Event> = repository
        .startAdvertising(essential = payload.essential, optional = payload.optional)
        .map { event ->
            when (event) {
                NCAdvertiserEvent.Registered -> Advertiser.Event.Started
                is NCAdvertiserEvent.Error -> Advertiser.Event.Failed(event.error)
            }
        }
        .onCompletion { cause -> if (cause == null) emit(Advertiser.Event.Stopped) }
}
