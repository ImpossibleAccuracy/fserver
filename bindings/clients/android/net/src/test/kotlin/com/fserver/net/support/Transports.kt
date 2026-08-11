package com.fserver.net.support

import com.fserver.net.spi.Transport
import com.fserver.net.spi.TransportCapabilities
import com.fserver.net.spi.TransportEndpoint
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import java.io.IOException

val DEAD: Transport.Id = Transport.Id("dead")

data class DeadEndpoint(val name: String) : TransportEndpoint {
    override val transport: Transport.Id = DEAD
    override val address: String = name
}

/** A route that always fails to open, for testing what the manager does with the next one. */
class DeadTransport : Transport {
    override val id: Transport.Id = DEAD
    override val capabilities = TransportCapabilities()

    override fun supports(endpoint: TransportEndpoint) = endpoint is DeadEndpoint

    override suspend fun open(endpoint: TransportEndpoint): Result<Transport.Channel> =
        Result.failure(IOException("dead transport"))

    override suspend fun shutdown() = Unit
}

/** Two ends of one in-memory link, for tests that drive the handshake without a manager. */
fun channelPair(): Pair<Transport.Channel, Transport.Channel> {
    val toB = Channel<ByteArray>(Channel.UNLIMITED)
    val toA = Channel<ByteArray>(Channel.UNLIMITED)
    return PipeChannel(LoopbackEndpoint("b"), toA, toB) to
            PipeChannel(LoopbackEndpoint("a"), toB, toA)
}

private class PipeChannel(
    override val endpoint: TransportEndpoint,
    private val incoming: Channel<ByteArray>,
    private val outgoing: Channel<ByteArray>,
) : Transport.Channel {
    override val inbound: Flow<ByteArray> = incoming.receiveAsFlow()

    override suspend fun send(frame: ByteArray): Result<Unit> = runCatching { outgoing.send(frame) }

    override fun close() {
        incoming.close()
        outgoing.close()
    }
}
