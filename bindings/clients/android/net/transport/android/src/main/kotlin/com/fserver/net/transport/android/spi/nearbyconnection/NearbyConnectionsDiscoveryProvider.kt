package com.fserver.net.transport.android.spi.nearbyconnection

import com.fserver.net.spi.DiscoveredEndpoint
import com.fserver.net.spi.DiscoveryProvider
import com.fserver.net.spi.DiscoveryProvider.Event.Failed
import com.fserver.net.spi.SpiId
import com.fserver.net.transport.android.datasource.nearbyconnection.NCDiscoveryEvent
import com.fserver.net.transport.android.datasource.nearbyconnection.NearbyConnectionsRepository
import com.fserver.net.transport.android.datasource.nearbyconnection.NearbyEndpointInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

internal class NearbyConnectionsDiscoveryProvider(
    private val repository: NearbyConnectionsRepository,
) : DiscoveryProvider {
    override val id: SpiId = NearbyConnectionsSPI.ID

    override fun accepts(params: DiscoveryProvider.ScanParams): Boolean =
        params is NearbyConnectionsScanParams

    /**
     * Reports what is in radio range. Nothing is dialled until `:net` asks the transport to open
     * one of these endpoints, so the digits a user compares only exist from that point on -
     * [DiscoveredEndpoint.confirmationCode] is null here.
     */
    override fun scan(params: DiscoveryProvider.ScanParams): Flow<DiscoveryProvider.Event> {
        require(accepts(params)) { "$id cannot serve $params" }

        return repository.startDiscovery().mapNotNull { event ->
            when (event) {
                NCDiscoveryEvent.Registered -> null
                is NCDiscoveryEvent.Error -> Failed(event.error)

                is NCDiscoveryEvent.EndpointFound -> {
                    val advertised = NearbyEndpointInfo.decode(event.endpointInfo)

                    DiscoveryProvider.Event.Appeared(
                        peer = DiscoveredEndpoint(
                            endpoint = NearbyConnectionsTransportEndpoint(event.endpointId),
                            advertisedName = advertised.displayName,
                            attributes = advertised.attributes,
                            confirmationCode = null,
                        )
                    )
                }

                is NCDiscoveryEvent.EndpointLost ->
                    DiscoveryProvider.Event.Disappeared(endpointAddress = event.endpointId)
            }
        }
    }
}
