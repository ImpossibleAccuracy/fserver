package com.fserver.net.transport.android.spi.nearbyconnection

import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.spi.DiscoveredEndpoint
import com.fserver.net.spi.GreetingSource
import com.fserver.net.spi.SpiId
import com.fserver.net.spi.Transport
import com.fserver.net.spi.TransportCapabilities
import com.fserver.net.spi.TransportEndpoint
import com.fserver.net.transport.android.datasource.nearbyconnection.NCDeviceEvent
import com.fserver.net.transport.android.datasource.nearbyconnection.NearbyConnectionsLink
import com.fserver.net.transport.android.datasource.nearbyconnection.NearbyConnectionsPeer
import com.fserver.net.transport.android.datasource.nearbyconnection.NearbyConnectionsRepository
import com.fserver.net.transport.android.datasource.nearbyconnection.NearbyEndpointInfo
import com.google.android.gms.nearby.connection.ConnectionsClient
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Carries frames over Nearby Connections.
 *
 * Nearby has no separate dial step: [open] runs the whole request-then-accept exchange and only
 * returns once the link is up, so a channel handed back here is one that can be written to.
 */
internal class NearbyConnectionsTransport(
    private val repository: NearbyConnectionsRepository,
) : Transport {
    override val id: SpiId = NearbyConnectionsSPI.ID

    override val capabilities: TransportCapabilities = TransportCapabilities(
        // Nearby refuses a bytes payload over its own limit
        maxFrameSize = ConnectionsClient.MAX_BYTES_DATA_SIZE,
        // Nearby encrypts the link and derives the digits from that key exchange, so comparing
        // them authenticates the channel. It says nothing about which device is on the other end.
        security = AuthMethodId.TransportConfirmation,
        // No anonymous phase: bytes cannot flow before the connection is accepted. What a peer
        // would learn from a greeting is already in the endpoint info, exchanged before that.
        greeting = GreetingSource.Transport,
    )

    override val listener: Transport.Listener = TransportListener()

    override fun supports(endpoint: TransportEndpoint): Boolean =
        endpoint is NearbyConnectionsTransportEndpoint

    override suspend fun open(endpoint: TransportEndpoint): Result<Transport.Channel> {
        if (endpoint !is NearbyConnectionsTransportEndpoint) {
            return Result.failure(
                IllegalArgumentException("not a nearby connections endpoint: ${endpoint.address}")
            )
        }

        return repository
            .connect(endpoint.endpointId)
            .map { link -> NearbyConnectionsChannel(link, repository) }
    }

    override suspend fun shutdown() {
        repository.shutdown()
    }

    private inner class TransportListener : Transport.Listener {
        /**
         * Only connections the peer asked for. The ones this device dialled are resolved inside
         * [open]; reporting them here too would have `:net` answer its own outgoing connection and
         * open a second session on the same link.
         */
        override fun listen(): Flow<Transport.InboundConnection> = repository.events
            .mapNotNull { event ->
                (event as? NCDeviceEvent.ConnectionInitiated)?.takeIf { it.incoming }
            }
            .map { event -> NearbyInboundConnection(event.peer, repository) }
    }

    private class NearbyInboundConnection(
        private val innerPeer: NearbyConnectionsPeer,
        private val repository: NearbyConnectionsRepository,
    ) : Transport.InboundConnection {
        private val settled = AtomicBoolean(false)

        override val transport: SpiId = NearbyConnectionsSPI.ID

        override val peer: DiscoveredEndpoint = run {
            val advertised = NearbyEndpointInfo.decode(innerPeer.endpointInfo)

            DiscoveredEndpoint(
                endpoint = NearbyConnectionsTransportEndpoint(innerPeer.endpointId),
                advertisedName = advertised.displayName,
                attributes = advertised.attributes,
                confirmationCode = innerPeer.authenticationDigits,
            )
        }

        override suspend fun accept(): Result<Transport.Channel> {
            if (!settled.compareAndSet(false, true)) {
                return Result.failure(IllegalStateException("Connection already settled"))
            }

            return repository
                .accept(innerPeer)
                .map { link -> NearbyConnectionsChannel(link, repository) }
        }

        override suspend fun reject() {
            if (!settled.compareAndSet(false, true)) return

            repository.reject(innerPeer.endpointId).getOrThrow()
        }
    }

    private class NearbyConnectionsChannel(
        private val link: NearbyConnectionsLink,
        private val repository: NearbyConnectionsRepository,
    ) : Transport.Channel {
        override val endpoint: TransportEndpoint =
            NearbyConnectionsTransportEndpoint(link.peer.endpointId)

        override val confirmationCode: String = link.peer.authenticationDigits

        override val inbound: Flow<ByteArray> = link.inbound

        override suspend fun send(frame: ByteArray): Result<Unit> =
            repository.send(link.peer.endpointId, frame)

        override fun close() {
            repository.disconnect(link.peer.endpointId)
        }
    }
}
