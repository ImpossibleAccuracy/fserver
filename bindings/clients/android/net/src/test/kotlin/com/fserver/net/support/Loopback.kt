package com.fserver.net.support

import com.fserver.net.spi.DiscoveredEndpoint
import com.fserver.net.spi.InboundConnection
import com.fserver.net.spi.Transport
import com.fserver.net.spi.TransportCapabilities
import com.fserver.net.spi.TransportChannel
import com.fserver.net.spi.TransportEndpoint
import com.fserver.net.spi.TransportId
import com.fserver.net.spi.TransportListener
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.Flow
import java.util.concurrent.ConcurrentHashMap

val LOOPBACK: TransportId = TransportId("loopback")

data class LoopbackEndpoint(val name: String) : TransportEndpoint {
    override val transport: TransportId = LOOPBACK
    override val address: String = name
}

/**
 * In-memory switchboard standing in for a real SPI implementation, so the session machinery can
 * be tested without a radio or a socket.
 */
class LoopbackNetwork {
    private val inboxes = ConcurrentHashMap<String, Channel<InboundConnection>>()
    private val wires = java.util.concurrent.CopyOnWriteArrayList<Channel<ByteArray>>()

    /** Kills every open link, as a radio going out of range would. */
    fun cutLinks() {
        wires.forEach { it.close() }
        wires.clear()
    }

    fun transport(self: String): Transport = LoopbackTransport(self)

    private inner class LoopbackTransport(private val self: String) : Transport {
        override val id: TransportId = LOOPBACK

        override val capabilities = TransportCapabilities(maxFrameSize = 64 * 1024)

        override fun supports(endpoint: TransportEndpoint) = endpoint is LoopbackEndpoint

        override suspend fun open(endpoint: TransportEndpoint): Result<TransportChannel> = runCatching {
            val target = endpoint as LoopbackEndpoint
            val inbox = inboxes.getOrPut(target.name) { Channel(Channel.UNLIMITED) }

            val toResponder = Channel<ByteArray>(Channel.UNLIMITED)
            val toInitiator = Channel<ByteArray>(Channel.UNLIMITED)
            wires += toResponder
            wires += toInitiator

            val responderSide = LoopbackChannel(LoopbackEndpoint(self), toResponder, toInitiator)
            val initiatorSide = LoopbackChannel(target, toInitiator, toResponder)

            inbox.send(
                LoopbackInbound(
                    channel = responderSide,
                    peer = DiscoveredEndpoint(
                        endpoint = LoopbackEndpoint(self),
                        advertisedName = self,
                    ),
                )
            )

            initiatorSide
        }

        override val listener: TransportListener = object : TransportListener {
            override fun listen(): Flow<InboundConnection> =
                inboxes.getOrPut(self) { Channel(Channel.UNLIMITED) }.receiveAsFlow()
        }
    }

    private class LoopbackChannel(
        override val endpoint: TransportEndpoint,
        private val incoming: Channel<ByteArray>,
        private val outgoing: Channel<ByteArray>,
    ) : TransportChannel {
        override val inbound: Flow<ByteArray> = incoming.receiveAsFlow()

        override suspend fun send(frame: ByteArray): Result<Unit> = runCatching { outgoing.send(frame) }

        override fun close() {
            incoming.close()
            outgoing.close()
        }
    }

    private class LoopbackInbound(
        private val channel: TransportChannel,
        override val peer: DiscoveredEndpoint,
    ) : InboundConnection {
        override val transport: TransportId = LOOPBACK

        override suspend fun accept(): Result<TransportChannel> = Result.success(channel)

        override suspend fun reject() = channel.close()
    }
}
