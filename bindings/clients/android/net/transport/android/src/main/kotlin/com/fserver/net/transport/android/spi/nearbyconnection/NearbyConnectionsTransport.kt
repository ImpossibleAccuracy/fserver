package com.fserver.net.transport.android.spi.nearbyconnection

import com.fserver.net.spi.DiscoveredEndpoint
import com.fserver.net.spi.SpiId
import com.fserver.net.spi.Transport
import com.fserver.net.spi.TransportCapabilities
import com.fserver.net.spi.TransportEndpoint
import com.fserver.net.transport.android.datasource.nearbyconnection.NCDeviceEvent
import com.fserver.net.transport.android.datasource.nearbyconnection.NearbyConnectionsPeer
import com.fserver.net.transport.android.datasource.nearbyconnection.NearbyConnectionsRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.takeWhile

internal class NearbyConnectionsTransport(
    private val repository: NearbyConnectionsRepository,
) : Transport {
    override val id: SpiId = NearbyConnectionsSPI.ID
    override val capabilities: TransportCapabilities = TransportCapabilities(
        isLinkEncrypted = true,
        requiresPeerConfirmation = true,
    )

    private val transportListener = TransportListener()
    override val listener: Transport.Listener = transportListener

    override fun supports(endpoint: TransportEndpoint): Boolean =
        endpoint is NearbyConnectionsTransportEndpoint

    override suspend fun open(endpoint: TransportEndpoint): Result<Transport.Channel> =
        runCatching {
            require(endpoint is NearbyConnectionsTransportEndpoint) {
                "not a nearby connections endpoint: ${endpoint.address}"
            }
            openConnection(endpoint)
        }.onFailure {
            if (it is CancellationException) throw it
        }


    private suspend fun openConnection(endpoint: NearbyConnectionsTransportEndpoint): Transport.Channel {
        return NearbyConnectionsChannel(
            peer = endpoint.peer,
            repository = repository,
        )
    }

    override suspend fun shutdown() {
        // no-op, transport just listens for repo's flows
    }

    private inner class TransportListener : Transport.Listener {
        override fun listen(): Flow<Transport.InboundConnection> = repository.events
            .filterIsInstance<NCDeviceEvent.Found>()
            .map { event ->
                NearbyInboundConnection(
                    innerPeer = event.peer,
                    repository = repository,
                )
            }
    }

    private class NearbyInboundConnection(
        private val innerPeer: NearbyConnectionsPeer,
        private val repository: NearbyConnectionsRepository,
    ) : Transport.InboundConnection {
        override val transport: SpiId = NearbyConnectionsSPI.ID
        override val peer: DiscoveredEndpoint = DiscoveredEndpoint(
            endpoint = NearbyConnectionsTransportEndpoint(innerPeer),
            advertisedName = innerPeer.endpointName,
            attributes = mapOf(), // TODO: attributes dropped, fix
            confirmationCode = innerPeer.authenticationDigits,
        )

        override suspend fun accept(): Result<Transport.Channel> = runCatching {
            repository.accept(innerPeer.endpointId)
            NearbyConnectionsChannel(
                peer = innerPeer,
                repository = repository
            )
        }

        override suspend fun reject() {
            repository.reject(innerPeer.endpointId)
        }
    }

    private class NearbyConnectionsChannel(
        private val peer: NearbyConnectionsPeer,
        private val repository: NearbyConnectionsRepository,
    ) : Transport.Channel {
        override val endpoint: TransportEndpoint = NearbyConnectionsTransportEndpoint(peer)
        override val inbound: Flow<ByteArray> = repository.events
            .takeWhile { event ->
                val isDisconnected = event is NCDeviceEvent.Disconnected &&
                        event.endpointId == peer.endpointId

                val isError = event is NCDeviceEvent.PeerError &&
                        event.id == peer.endpointId

                !(isDisconnected || isError)
            }
            .filterIsInstance<NCDeviceEvent.Message>()
            .filter { it.message.endpointId == peer.endpointId }
            .distinctUntilChangedBy { it.message.messageId }
            .map { it.message.message }

        override suspend fun send(frame: ByteArray): Result<Unit> {
            return repository.send(peer.endpointId, frame)
        }

        override fun close() {
            repository.disconnect(peer.endpointId)
        }
    }
}
