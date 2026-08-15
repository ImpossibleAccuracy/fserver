package com.fserver.net.connection.impl

import com.fserver.net.NetworkException
import com.fserver.net.config.NetworkConfig
import com.fserver.net.connection.IncomingConnectionsManager
import com.fserver.net.connection.PeerRef
import com.fserver.net.connection.throttle.HandshakeSource
import com.fserver.net.connection.throttle.HandshakeThrottle
import com.fserver.net.handshake.FramePump
import com.fserver.net.handshake.HandshakeNegotiator
import com.fserver.net.session.CloseReason
import com.fserver.net.session.PeerSession
import com.fserver.net.spi.DiscoveredEndpoint
import com.fserver.net.spi.SpiId
import com.fserver.net.spi.Transport
import com.fserver.net.utils.netRunCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

internal class IncomingConnectionsManagerImpl<M : Any>(
    private val config: NetworkConfig<M>,
    private val negotiator: HandshakeNegotiator,
    private val connectionsHolder: ConnectionsHolder<M>,
    private val scope: CoroutineScope,
) : IncomingConnectionsManager<M> {

    private val throttle = HandshakeThrottle(config.policy)

    override val sessions: StateFlow<List<PeerSession<M>>> = connectionsHolder.sessions
        .map { it.values.toList() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    override val incoming: Flow<IncomingConnectionsManager.IncomingRequest> = config.transports
        .mapNotNull { transport -> transport.listener?.listen()?.map { transport to it } }
        .merge()
        .let { connections ->
            channelFlow {
                connections.collect { (transport, connection) ->
                    // One coroutine each: a peer that says hello and goes quiet must not hold up
                    // next one, and the greeting can take as long as its deadline allows.
                    launch {
                        admit(
                            owner = transport,
                            connection = connection
                        )?.let { send(it) }
                    }
                }
            }
        }
        .shareIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            replay = 0,
        )

    override fun session(deviceId: String): PeerSession<M>? =
        connectionsHolder.sessionFor(deviceId)

    suspend fun shutdown() {
        connectionsHolder.sessions.value.values
            .forEach { it.close(CloseReason.Local("node closed")) }
    }

    /**
     * Answers an inbound connection and takes it through the public greeting. Returns null when
     * the source is throttled, or the peer never asked to authenticate - a probe, a scan, or a
     * link that simply died - so nothing is raised for it.
     */
    private suspend fun admit(
        owner: Transport,
        connection: Transport.InboundConnection,
    ): IncomingConnectionsManager.IncomingRequest? {
        val source = HandshakeSource(owner.id, connection.peer.endpoint)
        if (!throttle.reserve(source)) {
            config.logger.debug("refusing $source: pre-auth limit or rate limit reached")
            runCatching { connection.reject() }
            return null
        }

        val channel = connection.accept().getOrElse {
            throttle.release()
            config.logger.warn("could not answer ${connection.peer.advertisedName}", it)
            return null
        }

        val pump = FramePump(scope, channel)
        val inbound = try {
            negotiator.receive(pump, owner.capabilities, config.policy)
        } catch (e: Throwable) {
            throttle.release()
            pump.close()
            config.logger.debug("inbound from ${connection.peer.advertisedName} ended before auth: ${e.message}")
            return null
        }

        return IncomingRequest(
            source = source,
            connection = connection,
            confirmationCode = channel.confirmationCode,
            pump = pump,
            inbound = inbound,
        )
    }

    /**
     * A peer already past the greeting, holding an open link while the host decides.
     *
     * That link is a pre-authentication resource, so it is not held indefinitely: nobody settling
     * within the auth deadline is the same as a refusal.
     */
    private inner class IncomingRequest(
        private val source: HandshakeSource,
        private val connection: Transport.InboundConnection,
        override val confirmationCode: String?,
        private val pump: FramePump,
        private val inbound: HandshakeNegotiator.Inbound,
    ) : IncomingConnectionsManager.IncomingRequest {
        override val transport: SpiId = connection.transport
        override val peer: DiscoveredEndpoint = connection.peer

        private val settled = AtomicBoolean(false)

        /** Kills the request if the user never answers. */
        private val watchdog = scope.launch {
            delay(config.policy.authConfig.authTimeout)
            if (settled.compareAndSet(false, true)) {
                throttle.release()
                config.logger.debug("nobody answered the request from ${peer.advertisedName}")
                runCatching { inbound.reject("no answer") }
            }
        }

        override suspend fun accept(): Result<Unit> = netRunCatching {
            if (!settled.compareAndSet(false, true)) {
                throw NetworkException.Transport("request from ${peer.advertisedName} already settled")
            }
            watchdog.cancel()

            val link = try {
                pump.runOrAbort {
                    inbound.accept(
                        confirmationCode = confirmationCode,
                        policy = config.policy
                    )
                }
            } catch (e: Throwable) {
                if (e is NetworkException.AuthenticationRejected) {
                    throttle.onAuthenticationFailed(source)
                }
                pump.close()
                throw e
            } finally {
                throttle.release()
            }
            throttle.onAuthenticated(source)

            val session = connectionsHolder.register(
                route = PeerRef(
                    deviceId = link.negotiated.peer.deviceId,
                    transport = transport,
                    endpoint = peer.endpoint
                ),
                link = link,
                policy = config.policy,
                relink = null, // No relink: this side never dialed, so it has nothing to dial back.
            )

            config.logger.debug("accepted a session with ${session.route.deviceId}")
        }

        override suspend fun reject(reason: CloseReason) {
            if (!settled.compareAndSet(false, true)) return
            watchdog.cancel()
            throttle.release()
            try {
                inbound.reject(reason.toString())
            } catch (e: Exception) {
                throw NetworkException.Transport("could not reject ${peer.advertisedName}", e)
            }
        }
    }
}
