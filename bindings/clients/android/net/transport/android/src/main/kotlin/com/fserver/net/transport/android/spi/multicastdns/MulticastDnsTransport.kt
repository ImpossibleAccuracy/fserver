package com.fserver.net.transport.android.spi.multicastdns

import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.spi.DiscoveredEndpoint
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
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
class MulticastDnsTransport(
    private val multicastDnsPortBinder: MulticastDnsPortBinder,
    private val connectionPolicy: ConnectionPolicy,
) : Transport {
    override val id: Transport.Id = ID
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

        override val transport: Transport.Id = ID
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

    @OptIn(ExperimentalAtomicApi::class)
    private class SocketChannel(
        private val socket: Socket,
        override val endpoint: MulticastDnsTransportEndpoint,
        private val maxFrameSize: Int,
    ) : Transport.Channel {
        private val writeLock = Mutex()
        private val collected = AtomicBoolean(false)

        private val inputStream = DataInputStream(socket.getInputStream())
        private val outputStream = DataOutputStream(socket.getOutputStream().buffered())

        /** Collectable once: a second reader of the same stream would split frames in half. */
        override val inbound: Flow<ByteArray> = flow {
            check(collected.compareAndSet(false, true)) {
                "inbound of ${endpoint.address} is already being collected"
            }

            while (currentCoroutineContext().isActive) {
                val size = try {
                    inputStream.readInt()
                } catch (_: IOException) {
                    break // Socket closed
                }

                if (size !in 1..maxFrameSize) {
                    throw IOException("Invalid frame size: $size")
                }

                val frame = ByteArray(size)
                inputStream.readFully(frame)
                emit(frame)
            }
        }.flowOn(Dispatchers.IO)

        override suspend fun send(frame: ByteArray): Result<Unit> = writeLock.withLock {
            withContext(Dispatchers.IO) {
                try {
                    outputStream.writeInt(frame.size)
                    outputStream.write(frame)
                    outputStream.flush()

                    Result.success(Unit)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: IOException) {
                    Result.failure(e)
                }
            }
        }

        override fun close() {
            socket.closeQuietly()
        }
    }

    companion object {
        val ID = Transport.Id("multicast-dns")
    }
}

/** Teardown paths cannot do anything useful with a close failure. */
private fun Closeable.closeQuietly() {
    try {
        close()
    } catch (_: IOException) {
        // Already closed, or the peer is gone.
    }
}
