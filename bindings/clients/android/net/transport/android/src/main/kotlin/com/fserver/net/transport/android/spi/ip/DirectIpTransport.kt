package com.fserver.net.transport.android.spi.ip

import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.spi.SpiId
import com.fserver.net.spi.Transport
import com.fserver.net.spi.TransportCapabilities
import com.fserver.net.spi.TransportEndpoint
import com.fserver.net.transport.android.spi.LanDialer
import com.fserver.net.transport.android.spi.multicastdns.SocketChannel
import kotlinx.coroutines.CancellationException

internal class DirectIpTransport(
    private val connectionPolicy: ConnectionPolicy,
) : Transport {
    override val id: SpiId = DirectIpSPI.ID
    override val capabilities: TransportCapabilities = TransportCapabilities()

    override fun supports(endpoint: TransportEndpoint): Boolean =
        endpoint is DirectIpEndpoint

    override suspend fun open(endpoint: TransportEndpoint): Result<Transport.Channel> =
        runCatching {
            require(endpoint is DirectIpEndpoint) {
                "not a direct-ip endpoint: ${endpoint.address}"
            }
            openSocket(endpoint)
        }.onFailure {
            if (it is CancellationException) throw it
        }

    /**
     * The port comes from an address typed in, a QR code, or a route written down last time - all
     * of which go stale when the peer restarts onto another port of the fixed list, so the dial
     * falls through to the rest of it. The channel carries the port that answered.
     */
    private suspend fun openSocket(endpoint: DirectIpEndpoint): SocketChannel {
        val socket = LanDialer.connect(
            host = endpoint.host,
            ports = LanDialer.ports(endpoint.port),
            timeout = connectionPolicy.timeouts.connect,
        )

        return SocketChannel(
            socket = socket,
            endpoint = DirectIpEndpoint(host = endpoint.host, port = socket.port),
            maxFrameSize = capabilities.maxFrameSize,
        )
    }

    override suspend fun shutdown() {
        // Nothing to do here, this transport doesn't listen for incoming connections
    }
}
