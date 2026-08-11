package com.fserver.net.transport.android.spi.nearbyconnection

import com.fserver.net.security.IdentityStore
import com.fserver.net.spi.DiscoveredEndpoint
import com.fserver.net.spi.DiscoveryProvider
import com.fserver.net.spi.SpiId
import com.fserver.net.transport.android.datasource.nearbyconnection.NCDeviceEvent
import com.fserver.net.transport.android.datasource.nearbyconnection.NCDiscoveryEvent
import com.fserver.net.transport.android.datasource.nearbyconnection.NearbyConnectionsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull

internal class NearbyConnectionsDiscoveryProvider(
    private val identityStore: IdentityStore,
    private val repository: NearbyConnectionsRepository,
) : DiscoveryProvider {
    override val id: SpiId = NearbyConnectionsSPI.ID

    override fun accepts(params: DiscoveryProvider.ScanParams): Boolean =
        params is NearbyConnectionsScanParams

    override fun scan(params: DiscoveryProvider.ScanParams): Flow<DiscoveryProvider.Event> {
        require(accepts(params)) { "$id cannot serve $params" }

        return repository
            .startDiscovery(identityStore.local)
            .mapNotNull { event ->
                when (event) {
                    is NCDeviceEvent.Found ->
                        DiscoveryProvider.Event.Appeared(
                            peer = DiscoveredEndpoint(
                                endpoint = NearbyConnectionsTransportEndpoint(event.peer),
                                advertisedName = event.peer.endpointName,
                                attributes = mapOf(), // TODO: attributes dropped, fix
                                confirmationCode = event.peer.authenticationDigits,
                            )
                        )

                    is NCDeviceEvent.Disconnected ->
                        DiscoveryProvider.Event.Disappeared(
                            endpointAddress = event.endpointId,
                        )

                    is NCDiscoveryEvent.Error ->
                        DiscoveryProvider.Event.Failed(event.error)

                    is NCDeviceEvent.PeerError ->
                        DiscoveryProvider.Event.Failed(
                            RuntimeException(
                                "Peer error for ${event.id}: ${event.errorCode}",
                            )
                        )

                    else -> null
                }
            }
    }
}
