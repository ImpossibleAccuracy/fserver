package com.fserver.net.support

import com.fserver.net.spi.DiscoveredEndpoint
import com.fserver.net.spi.SpiId
import com.fserver.net.spi.Transport
import com.fserver.net.spi.TransportCapabilities
import com.fserver.net.spi.TransportEndpoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.util.concurrent.ConcurrentHashMap

val LOOPBACK: SpiId = SpiId("loopback")

data class LoopbackEndpoint(val name: String) : TransportEndpoint {
    override val transport: SpiId = LOOPBACK
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

    /**
     * @param capabilities what this end declares. Tests that exercise transport-supplied auth pass
     * a [com.fserver.net.spi.ChannelSecurity.Sas] here.
     * @param confirmationCode the out-of-band string both ends of this link report. Both sides get
     * the dialler's value, which is what a real SAS transport does.
     */
    fun transport(
        self: String,
        capabilities: TransportCapabilities = DEFAULT_CAPABILITIES,
        confirmationCode: String? = null,
    ): Transport = LoopbackTransport(self, capabilities, confirmationCode)

    private inner class LoopbackTransport(
        private val self: String,
        override val capabilities: TransportCapabilities,
        private val confirmationCode: String?,
    ) : Transport {
        override val id: SpiId = LOOPBACK

        override fun supports(endpoint: TransportEndpoint) = endpoint is LoopbackEndpoint

        override suspend fun open(endpoint: TransportEndpoint): Result<Transport.Channel> =
            runCatching {
                val target = endpoint as LoopbackEndpoint
                val inbox = inboxes.getOrPut(target.name) { Channel(Channel.UNLIMITED) }

                val toResponder = Channel<ByteArray>(Channel.UNLIMITED)
                val toInitiator = Channel<ByteArray>(Channel.UNLIMITED)
                wires += toResponder
                wires += toInitiator

                val responderSide = LoopbackChannel(
                    LoopbackEndpoint(self), toResponder, toInitiator, confirmationCode
                )
                val initiatorSide =
                    LoopbackChannel(target, toInitiator, toResponder, confirmationCode)

                inbox.send(
                    LoopbackInbound(
                        channel = responderSide,
                        peer = DiscoveredEndpoint(
                            endpoint = LoopbackEndpoint(self),
                            advertisedName = self,
                            confirmationCode = confirmationCode,
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
        override val confirmationCode: String?,
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
        override val transport: SpiId = LOOPBACK

        override suspend fun accept(): Result<Transport.Channel> = Result.success(channel)

        override suspend fun reject() = channel.close()
    }

    private companion object {
        val DEFAULT_CAPABILITIES = TransportCapabilities(maxFrameSize = 64 * 1024)
    }
}
