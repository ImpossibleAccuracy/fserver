package com.fserver.net.support

import com.fserver.net.spi.DiscoveredEndpoint
import com.fserver.net.spi.Transport
import com.fserver.net.spi.TransportCapabilities
import com.fserver.net.spi.TransportEndpoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.ConcurrentHashMap

val LOOPBACK: Transport.Id = Transport.Id("loopback")

data class LoopbackEndpoint(val name: String) : TransportEndpoint {
    override val transport: Transport.Id = LOOPBACK
    override val address: String = name
}

/**
 * In-memory switchboard standing in for a real SPI implementation, so the session machinery can
 * be tested without a radio or a socket.
 */
class LoopbackNetwork {
    private val inboxes = ConcurrentHashMap<String, Channel<Transport.InboundConnection>>()
    private val wires = java.util.concurrent.CopyOnWriteArrayList<Channel<ByteArray>>()
    private val sent = java.util.concurrent.CopyOnWriteArrayList<ByteArray>()

    @Volatile
    private var delivering = true

    /** Every frame handed to the wire, for tests that check what actually goes out. */
    val wireFrames: List<ByteArray> get() = sent.toList()

    /** Kills every open link, as a radio going out of range would. */
    fun cutLinks() {
        wires.forEach { it.close() }
        wires.clear()
    }

    /** Keeps the links open but stops delivering - what a peer gone silent looks like. */
    fun muteLinks() {
        delivering = false
    }

    fun transport(self: String): Transport = LoopbackTransport(self)

    private inner class LoopbackTransport(private val self: String) : Transport {
        override val id: Transport.Id = LOOPBACK

        override val capabilities = TransportCapabilities(maxFrameSize = 64 * 1024)

        override fun supports(endpoint: TransportEndpoint) = endpoint is LoopbackEndpoint

        override suspend fun open(endpoint: TransportEndpoint): Result<Transport.Channel> =
            runCatching {
                val target = endpoint as LoopbackEndpoint
                val inbox = inboxes.getOrPut(target.name) { Channel(Channel.UNLIMITED) }

                val toResponder = Channel<ByteArray>(Channel.UNLIMITED)
                val toInitiator = Channel<ByteArray>(Channel.UNLIMITED)
                wires += toResponder
                wires += toInitiator

                val responderSide =
                    LoopbackChannel(LoopbackEndpoint(self), toResponder, toInitiator)
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

        override val listener: Transport.Listener = object : Transport.Listener {
            override fun listen(): Flow<Transport.InboundConnection> =
                inboxes.getOrPut(self) { Channel(Channel.UNLIMITED) }.receiveAsFlow()
        }

        override suspend fun shutdown() {
        }
    }

    private inner class LoopbackChannel(
        override val endpoint: TransportEndpoint,
        private val incoming: Channel<ByteArray>,
        private val outgoing: Channel<ByteArray>,
    ) : Transport.Channel {
        override val inbound: Flow<ByteArray> = incoming.receiveAsFlow()

        override suspend fun send(frame: ByteArray): Result<Unit> = runCatching {
            sent += frame
            if (delivering) outgoing.send(frame)
        }

        override fun close() {
            incoming.close()
            outgoing.close()
        }
    }

    private class LoopbackInbound(
        private val channel: Transport.Channel,
        override val peer: DiscoveredEndpoint,
    ) : Transport.InboundConnection {
        override val transport: Transport.Id = LOOPBACK

        override suspend fun accept(): Result<Transport.Channel> = Result.success(channel)

        override suspend fun reject() = channel.close()
    }
}
