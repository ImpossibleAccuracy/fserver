package com.fserver.net.transport.android.spi.ip

import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.spi.SpiId
import com.fserver.net.spi.Transport
import com.fserver.net.spi.TransportCapabilities
import com.fserver.net.spi.TransportEndpoint
import com.fserver.net.transport.android.spi.multicastdns.SocketChannel
import com.fserver.net.transport.android.spi.multicastdns.closeQuietly
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.net.InetSocketAddress
import java.net.Socket

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

    private suspend fun openSocket(endpoint: DirectIpEndpoint): SocketChannel =
        withContext(Dispatchers.IO) {
            val socket = Socket()

            try {
                socket.connect(
                    /* endpoint = */ InetSocketAddress(endpoint.host, endpoint.port),
                    /* timeout = */ connectionPolicy.connectTimeout.inWholeMilliseconds.toInt()
                )
                // A connect that lands after the caller gave up would otherwise leak the socket.
                currentCoroutineContext().ensureActive()
            } catch (t: Throwable) {
                socket.closeQuietly()
                throw t
            }

            SocketChannel(
                socket = socket,
                endpoint = endpoint,
                maxFrameSize = capabilities.maxFrameSize,
            )
        }

    override suspend fun shutdown() {
        // Nothing to do here, this transport doesn't listen for incoming connections
    }
}
