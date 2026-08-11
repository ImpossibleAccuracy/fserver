package com.fserver.net.transport.android.spi.multicastdns

import android.content.Context
import com.fserver.net.spi.DiscoveredEndpoint
import com.fserver.net.spi.DiscoveryProvider
import com.fserver.net.transport.android.datasource.multicastdns.MulticastDnsDiscoveryService
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import java.io.IOException

class MulticastDnsDiscoveryProvider(
    private val context: Context,
) : DiscoveryProvider {
    private val discoveryService = MulticastDnsDiscoveryService(context)

    override val id: DiscoveryProvider.Id = ID

    override fun accepts(params: DiscoveryProvider.ScanParams): Boolean =
        params is MulticastDnsScanParams

    override fun scan(
        params: DiscoveryProvider.ScanParams
    ): Flow<DiscoveryProvider.Event> {
        require(accepts(params)) { "$id cannot serve $params" }

        return discoveryService.start().mapNotNull { discoveryEvent ->
            when (discoveryEvent) {
                MulticastDnsDiscoveryService.Event.Idle,
                MulticastDnsDiscoveryService.Event.Scanning,
                MulticastDnsDiscoveryService.Event.Closed -> null

                // Scan-level failure: NSD has stopped browsing, so the flow fails rather than
                // reporting an event and then quietly completing as if the scan had finished.
                is MulticastDnsDiscoveryService.Event.Error -> throw IOException(
                    "Multicast DNS discovery failed with error code: ${discoveryEvent.errorCode}"
                )

                is MulticastDnsDiscoveryService.Event.Found -> DiscoveryProvider.Event.Appeared(
                    DiscoveredEndpoint(
                        endpoint = discoveryEvent.peer.asEndpoint(),
                        advertisedName = discoveryEvent.peer.name,
                        attributes = discoveryEvent.peer.attributes,
                        confirmationCode = null,
                    )
                )

                is MulticastDnsDiscoveryService.Event.Disconnected ->
                    DiscoveryProvider.Event.Disappeared(
                        discoveryEvent.peer.asEndpoint().address
                    )

                // One peer failed to resolve; the scan carries on.
                is MulticastDnsDiscoveryService.Event.PeerError ->
                    DiscoveryProvider.Event.Failed(
                        IOException(
                            "Received error from peer ${discoveryEvent.id}: ${discoveryEvent.errorCode}"
                        )
                    )
            }
        }
    }

    companion object {
        /** Matches [MulticastDnsTransport.ID]: one method, one transport that carries it. */
        val ID = DiscoveryProvider.Id("multicast-dns")
    }
}
