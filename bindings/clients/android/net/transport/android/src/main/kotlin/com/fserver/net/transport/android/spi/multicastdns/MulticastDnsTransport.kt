package com.fserver.net.transport.android.spi.multicastdns

import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.spi.DiscoveredEndpoint
import com.fserver.net.spi.SpiId
import com.fserver.net.spi.Transport
import com.fserver.net.spi.TransportCapabilities
import com.fserver.net.spi.TransportEndpoint
import com.fserver.net.transport.android.datasource.multicastdns.MulticastDnsPortBinder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.atomic.AtomicReference
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * TCP carrier for peers found over mDNS. Its listener binds the port [MulticastDnsAdvertiser]
 * publishes, so both must share one [MulticastDnsPortBinder] and `listen()` has to be collected
 * before advertising starts.
 *
 * See `CONNECTION_FLOW.md` in this package for how advertising, discovery, dialling and accepting
 * fit together.
 */
internal class MulticastDnsTransport(
    private val multicastDnsPortBinder: MulticastDnsPortBinder,
    private val connectionPolicy: ConnectionPolicy,
) : Transport {
    override val id: SpiId = MulticastDnsSPI.ID
    override val capabilities: TransportCapabilities = TransportCapabilities()

    private val transportListener = TransportListener()
    override val listener: Transport.Listener = transportListener

    override fun supports(endpoint: TransportEndpoint): Boolean =
        endpoint is MulticastDnsTransportEndpoint && endpoint.isDialable

    override suspend fun open(endpoint: TransportEndpoint): Result<Transport.Channel> =
        runCatching {
            require(endpoint is MulticastDnsTransportEndpoint && endpoint.isDialable) {
                "not a dialable multicast-dns endpoint: ${endpoint.address}"
            }
            openSocket(endpoint)
        }.onFailure {
            if (it is CancellationException) throw it
        }

    private suspend fun openSocket(endpoint: MulticastDnsTransportEndpoint): SocketChannel =
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
        withContext(Dispatchers.IO) {
            // Closing the server socket unblocks accept(), which ends the listen() flow.
            transportListener.serverSocket.getAndSet(null)?.closeQuietly()
        }
    }

    private inner class TransportListener : Transport.Listener {
        val serverSocket = AtomicReference<ServerSocket?>(null)

        override fun listen(): Flow<Transport.InboundConnection> = channelFlow {
            val server = ServerSocket(0)
            if (!serverSocket.compareAndSet(null, server)) {
                server.closeQuietly()
                throw IllegalStateException("multicast-dns listener is already running")
            }

            multicastDnsPortBinder.set(server.localPort)

            val acceptLoop = launch {
                while (currentCoroutineContext().isActive) {
                    val socket = try {
                        server.accept()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: IOException) {
                        break // Server socket closed by shutdown() or by teardown below.
                    } catch (t: Throwable) {
                        close(t)
                        return@launch
                    }

                    send(
                        InboundConnection(
                            socket = socket,
                            capabilities = capabilities,
                        )
                    )
                }

                // Nothing more will arrive; let collectors see the flow finish.
                close()
            }

            awaitClose {
                multicastDnsPortBinder.set(null)
                acceptLoop.cancel()
                serverSocket.compareAndSet(server, null)
                server.closeQuietly()
            }
        }.flowOn(Dispatchers.IO)
    }

    @OptIn(ExperimentalAtomicApi::class)
    private class InboundConnection(
        private val socket: Socket,
        private val capabilities: TransportCapabilities,
    ) : Transport.InboundConnection {
        private val settled = AtomicBoolean(false)

        // socket.port is the peer's ephemeral source port, not the port it listens on, so this
        // endpoint says where the connection came from and is not dialable. The route to call the
        // peer back on comes from discovery, keyed on the device id the handshake confirms.
        private val endpoint = MulticastDnsTransportEndpoint(
            host = socket.inetAddress.hostAddress.orEmpty(),
            port = socket.port,
            isDialable = false,
        )

        override val transport: SpiId = MulticastDnsSPI.ID
        override val peer: DiscoveredEndpoint = DiscoveredEndpoint(
            endpoint = endpoint,
            advertisedName = socket.inetAddress.hostAddress.orEmpty(),
            attributes = emptyMap(),
            confirmationCode = null,
        )

        override suspend fun accept(): Result<Transport.Channel> {
            if (!settled.compareAndSet(false, true)) {
                return Result.failure(IllegalStateException("Connection already settled"))
            }

            return Result.success(
                SocketChannel(
                    socket = socket,
                    endpoint = endpoint,
                    maxFrameSize = capabilities.maxFrameSize,
                )
            )
        }

        override suspend fun reject() {
            if (!settled.compareAndSet(false, true)) return

            withContext(Dispatchers.IO) {
                socket.close()
            }
        }
    }
}
