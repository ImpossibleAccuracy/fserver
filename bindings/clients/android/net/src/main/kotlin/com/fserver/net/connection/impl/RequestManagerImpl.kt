package com.fserver.net.connection.impl

import com.fserver.net.NetworkException
import com.fserver.net.config.NetworkConfig
import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.connection.HandshakeProfile
import com.fserver.net.connection.PeerRef
import com.fserver.net.connection.RequestManager
import com.fserver.net.discovery.DiscoveredPeer
import com.fserver.net.handshake.FramePump
import com.fserver.net.handshake.HandshakeNegotiator
import com.fserver.net.peer.PublicGreeting
import com.fserver.net.security.auth.AuthRequest
import com.fserver.net.session.CloseReason
import com.fserver.net.session.PeerSession
import com.fserver.net.session.SessionLink
import com.fserver.net.spi.GreetingSource
import com.fserver.net.spi.Transport
import com.fserver.net.spi.TransportEndpoint
import com.fserver.net.utils.netRunCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withTimeoutOrNull

internal class RequestManagerImpl<M : Any>(
    private val config: NetworkConfig<M>,
    private val negotiator: HandshakeNegotiator,
    private val connectionsHolder: ConnectionsHolder<M>,
    private val scope: CoroutineScope,
) : RequestManager<M> {
    private val selector = TransportSelector(config.transports)

    override val profiles: StateFlow<Map<String, HandshakeProfile>> =
        connectionsHolder.profiles

    override suspend fun probe(
        peer: PeerRef,
        policy: ConnectionPolicy?
    ): Result<PublicGreeting> = netRunCatching {
        val policy = policy ?: config.policy
        val transport = selector.forEndpoint(peer.endpoint)
            ?: throw NetworkException.NoRoute("no transport carries ${peer.endpoint.address}")

        when (transport.capabilities.greeting) {
            GreetingSource.Wire -> Unit
            // Nearby and its kind cannot exchange bytes before the connection is answered,
            // so their greeting rides in the endpoint info and only discovery has it.
            GreetingSource.Transport -> throw NetworkException.NoRoute(
                "${transport.id.value} carries its greeting out of band; probe a discovered peer"
            )

            GreetingSource.None -> throw NetworkException.NoRoute(
                "${transport.id.value} has no greeting"
            )
        }

        val channel = openChannel(
            transport = transport,
            endpoint = peer.endpoint,
            policy = policy,
        )

        val pump = FramePump(scope = scope, channel = channel)
        pump.use { pump ->
            negotiator.greet(
                pump = pump,
                capabilities = transport.capabilities,
                policy = policy
            )
        }
    }

    override suspend fun probe(
        peer: DiscoveredPeer,
        policy: ConnectionPolicy?
    ): Result<PublicGreeting> = netRunCatching {
        val policy = policy ?: config.policy

        // Transport that cannot hold an anonymous conversation answers for itself
        // and costs no connection at all.
        peer.routes
            .firstNotNullOfOrNull { selector.forEndpoint(it.endpoint)?.capabilities }
            ?.takeIf { it.greeting == GreetingSource.Transport }
            ?.let { capabilities ->
                return@netRunCatching PublicGreeting(
                    // Versions are the peer's claim, from the air.
                    protocolVersions = peer.advertised.protocolVersions ?: IntRange.EMPTY,
                    // The method is not: such transport fixes it, and this side reads that
                    // off its own declaration rather than believing a broadcast.
                    methods = listOfNotNull(capabilities.security),
                )
            }

        overRoutes(
            peer = peer,
            policy = policy,
            what = "probe",
            attempt = { route ->
                probe(peer = route, policy = policy)
            },
        )
    }

    override suspend fun connect(
        peer: PeerRef,
        policy: ConnectionPolicy?,
        request: AuthRequest?
    ): Result<PeerSession<M>> = netRunCatching {
        connectionsHolder.sessionFor(peer.deviceId)?.let { return@netRunCatching it }

        val policy = policy ?: config.policy
        val link = openLink(peer, policy, request)
        connectionsHolder.register(
            route = peer,
            link = link,
            policy = policy,
            relink = { openLink(peer, policy, request) },
        )
    }

    override suspend fun connect(
        peer: DiscoveredPeer,
        policy: ConnectionPolicy?,
        request: AuthRequest?
    ): Result<PeerSession<M>> = netRunCatching {
        val deviceId = peer.advertised.deviceId
        connectionsHolder.sessionFor(deviceId)?.let { return@netRunCatching it }

        val policy = policy ?: config.policy
        overRoutes(
            peer = peer,
            policy = policy,
            what = "connect",
            attempt = { route ->
                connect(route, policy, request)
            },
        )
    }

    override fun profile(deviceId: String): HandshakeProfile? =
        connectionsHolder.profiles.value[deviceId]

    override suspend fun disconnect(
        deviceId: String,
        reason: CloseReason
    ) {
        connectionsHolder.sessions.value[deviceId]?.close(reason)
        // Clear cached handshake after disconnect
        connectionsHolder.forgetDevice(deviceId)
    }

    suspend fun shutdown() {
        connectionsHolder.reset()
        config.transports.forEach { runCatching { it.shutdown() } }
    }

    // ------------------------------------------------------------------ internals

    /** Tries every route in policy order and returns the first that works. */
    private inline fun <T> overRoutes(
        peer: DiscoveredPeer,
        policy: ConnectionPolicy,
        what: String,
        attempt: (PeerRef) -> Result<T>,
    ): T {
        val deviceId = peer.advertised.deviceId
        val routes = selector.order(peer.routes, policy)
        if (routes.isEmpty()) throw NetworkException.NoRoute("device $deviceId has no known route")

        var lastFailure: Throwable? = null
        for (route in routes) {
            val outcome = attempt(route)
            outcome.getOrNull()?.let { return it }
            lastFailure = outcome.exceptionOrNull()
            config.logger.warn(
                "$what over ${route.transport.value} failed for $deviceId",
                lastFailure
            )
        }

        throw lastFailure ?: NetworkException.NoRoute("device $deviceId unreachable")
    }

    /**
     * Opens a transport channel to [endpoint] and returns it,
     * or throws if the transport fails or times out.
     */
    private suspend fun openChannel(
        transport: Transport,
        endpoint: TransportEndpoint,
        policy: ConnectionPolicy,
    ): Transport.Channel =
        withTimeoutOrNull(policy.timeouts.connect) { transport.open(endpoint) }
            ?.getOrElse {
                throw NetworkException.Transport(
                    "could not open ${endpoint.address}",
                    it
                )
            }
            ?: throw NetworkException.Transport("timed out opening ${endpoint.address}")

    /**
     * Connect to [peer] and negotiate a session link. The greeting runs again here rather than
     * being carried over from a probe: the authentication is bound to the hello of its own link,
     * so there is nothing to hand across and nothing to substitute.
     */
    private suspend fun openLink(
        peer: PeerRef,
        policy: ConnectionPolicy,
        request: AuthRequest?,
    ): SessionLink {
        val transport = selector.forEndpoint(peer.endpoint)
            ?: throw NetworkException.NoRoute("no transport carries ${peer.endpoint.address}")

        val channel = openChannel(transport, peer.endpoint, policy)
        val pump = FramePump(scope, channel)
        return try {
            negotiator.connect(
                pump = pump,
                capabilities = transport.capabilities,
                confirmationCode = channel.confirmationCode,
                policy = policy,
                request = request,
            )
        } catch (e: Throwable) {
            pump.close()
            throw e
        }
    }
}
