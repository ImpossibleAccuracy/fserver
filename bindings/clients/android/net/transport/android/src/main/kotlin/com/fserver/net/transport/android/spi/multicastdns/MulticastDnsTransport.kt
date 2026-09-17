package com.fserver.net.transport.android.spi.multicastdns

import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.connection.LanPorts
import com.fserver.net.spi.DiscoveredEndpoint
import com.fserver.net.spi.SpiId
import com.fserver.net.spi.Transport
import com.fserver.net.spi.TransportCapabilities
import com.fserver.net.spi.TransportEndpoint
import com.fserver.net.transport.android.datasource.multicastdns.MulticastDnsPortBinder
import com.fserver.net.transport.android.spi.LanDialer
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.IOException
import java.net.Inet4Address
import java.net.InetSocketAddress
import java.net.NetworkInterface
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
        endpoint is MulticastDnsTransportEndpoint

    override suspend fun open(endpoint: TransportEndpoint): Result<Transport.Channel> =
        runCatching {
            require(endpoint is MulticastDnsTransportEndpoint) {
                "not a multicast-dns endpoint: ${endpoint.address}"
            }
            openSocket(endpoint)
        }.onFailure {
            if (it is CancellationException) throw it
        }

    /**
     * A discovered route leads with the port the scan resolved; an inbound one contributes only
     * its host, since its port is the peer's source port. Either way [LanDialer] fills in the rest.
     *
     * The channel carries the port that answered, so the route recorded from it is dialable.
     */
    private suspend fun openSocket(endpoint: MulticastDnsTransportEndpoint): SocketChannel {
        val socket = LanDialer.connect(
            host = endpoint.host,
            ports = LanDialer.ports(endpoint.port.takeIf { endpoint.isDialable }),
            timeout = connectionPolicy.timeouts.connect,
        )

        return SocketChannel(
            socket = socket,
            endpoint = MulticastDnsTransportEndpoint(host = endpoint.host, port = socket.port),
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

        /**
         * The bound port against every address this device currently answers on - the listener
         * binds the wildcard, so the port alone is not something a peer can be handed.
         */
        override val endpoints: Flow<List<TransportEndpoint>> =
            multicastDnsPortBinder.value.map { port ->
                if (port == null) {
                    emptyList()
                } else {
                    localHostAddresses().map { host ->
                        MulticastDnsTransportEndpoint(host = host, port = port)
                    }
                }
            }.flowOn(Dispatchers.IO)

        /**
         * Takes the first free port of [LanPorts.PREFERRED], so a route stored for this device
         * still reaches it after a restart. An ephemeral port is the fallback and costs exactly
         * that: peers holding a route to the old one have to discover this device again.
         */
        private fun bindListener(): ServerSocket {
            for (port in LanPorts.PREFERRED) {
                val server = ServerSocket()
                SocketTuning.beforeBind(server)

                try {
                    server.bind(InetSocketAddress(port))
                    return server
                } catch (t: IOException) {
                    // Another app holds it, or another profile of this one.
                    Timber.i(t, "port $port is taken, trying the next one")
                    server.closeQuietly()
                }
            }

            Timber.w("every preferred port is taken, falling back to an ephemeral one")

            return ServerSocket().also {
                SocketTuning.beforeBind(it)
                it.bind(InetSocketAddress(0))
            }
        }

        override fun listen(): Flow<Transport.InboundConnection> = channelFlow {
            val server = bindListener()

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
        // endpoint says where the connection came from and this exact pair is never dialled back.
        // Dialling it keeps the host and guesses the port off the fixed list; a route discovery
        // resolved is still better, since it names the port the peer actually took.
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

/**
 * Addresses of this device other machines on the link can dial - IPv4 only, since that is what the
 * MVP code format carries, and loopback left out because a peer can never reach it.
 */
private fun localHostAddresses(): List<String> = try {
    NetworkInterface.getNetworkInterfaces()
        .asSequence()
        .filter { it.isUp && !it.isLoopback }
        .flatMap { it.inetAddresses.asSequence() }
        .filterIsInstance<Inet4Address>()
        .mapNotNull { it.hostAddress }
        .toList()
} catch (e: IOException) {
    Timber.w(e, "could not enumerate local addresses")
    emptyList()
}
