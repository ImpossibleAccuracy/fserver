package com.fserver.net.connection

import com.fserver.net.NetLogger
import com.fserver.net.NetworkException
import com.fserver.net.dictionary.MessageDictionary
import com.fserver.net.discovery.DiscoveredPeer
import com.fserver.net.handshake.FramePump
import com.fserver.net.handshake.HandshakeNegotiator
import com.fserver.net.security.CryptoProvider
import com.fserver.net.security.IdentityStore
import com.fserver.net.security.PeerAuthenticator
import com.fserver.net.session.CloseReason
import com.fserver.net.session.PeerSession
import com.fserver.net.session.PeerSessionImpl
import com.fserver.net.session.SessionLink
import com.fserver.net.spi.DiscoveredEndpoint
import com.fserver.net.spi.SpiId
import com.fserver.net.spi.Transport
import com.fserver.net.utils.netRunCatching
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal class ConnectionManagerImpl<M : Any>(
    private val transports: List<Transport>,
    private val dictionary: MessageDictionary<M>,
    private val identityStore: IdentityStore,
    crypto: CryptoProvider,
    authenticator: PeerAuthenticator?,
    private val defaultPolicy: ConnectionPolicy,
    private val protocolVersions: IntRange,
    private val logger: NetLogger,
    private val scope: CoroutineScope,
) : ConnectionManager<M> {

    private val selector = TransportSelector(transports)

    private val negotiator = HandshakeNegotiator(
        identityStore = identityStore,
        dictionary = dictionary,
        crypto = crypto,
        authenticator = authenticator,
        protocolVersions = protocolVersions,
        logger = logger,
    )

    // One session per deviceId, whatever route it came in on.
    private val registry = MutableStateFlow<Map<String, PeerSessionImpl<M>>>(emptyMap())
    private val registryLock = Mutex()

    override val sessions: StateFlow<List<PeerSession<M>>> = registry
        .map { it.values.toList() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    // Outlives the session it came from: pairing looks at a device it has deliberately hung up on.
    private val profileRegistry =
        MutableStateFlow<Map<String, ConnectionManager.Profile>>(emptyMap())

    override val profiles: StateFlow<Map<String, ConnectionManager.Profile>> =
        profileRegistry.asStateFlow()

    override val incoming: Flow<ConnectionManager.IncomingRequest> = transports
        .mapNotNull { transport ->
            transport.listener?.listen()?.map { connection ->
                IncomingRequest(transport, connection)
            }
        }
        .merge()
        .shareIn(
            scope = scope,
            started = SharingStarted.Eagerly,
            replay = 0,
        )

    override suspend fun connect(peer: PeerRef, policy: ConnectionPolicy?): Result<PeerSession<M>> =
        netRunCatching {
            session(peer.deviceId)?.let { return@netRunCatching it }

            val policy = policy ?: defaultPolicy
            val link = openLink(peer, policy)
            register(
                route = peer,
                link = link,
                policy = policy,
                relink = { openLink(peer, policy) },
            )
        }

    override suspend fun connect(
        peer: DiscoveredPeer,
        policy: ConnectionPolicy?
    ): Result<PeerSession<M>> =
        netRunCatching {
            val deviceId = peer.descriptor.deviceId
            session(deviceId)?.let { return@netRunCatching it }

            val policy = policy ?: defaultPolicy
            val routes = selector.order(peer.routes, policy)
            if (routes.isEmpty()) throw NetworkException.NoRoute("device $deviceId has no known route")

            var lastFailure: Throwable? = null
            for (route in routes) {
                val attempt = connect(route, policy)
                attempt.getOrNull()?.let { return@netRunCatching it }
                lastFailure = attempt.exceptionOrNull()
                logger.warn(
                    "route ${route.transport.value} failed for $deviceId",
                    lastFailure
                )
            }

            throw lastFailure ?: NetworkException.NoRoute("device $deviceId unreachable")
        }

    override fun session(deviceId: String): PeerSession<M>? = registry.value[deviceId]

    override fun profile(deviceId: String): ConnectionManager.Profile? =
        profileRegistry.value[deviceId]

    override suspend fun disconnect(deviceId: String, reason: CloseReason) {
        registry.value[deviceId]?.close(reason)
        // Clear cached handshake after disconnect
        profileRegistry.update { it - deviceId }
    }

    override suspend fun probe(
        peer: PeerRef,
        policy: ConnectionPolicy?
    ): Result<ConnectionManager.Profile> =
        netRunCatching {
            val link = openLink(peer, policy ?: defaultPolicy)
            try {
                rememberProfile(peer, link)
            } finally {
                link.secure.close()
            }
        }

    override suspend fun probe(
        peer: DiscoveredPeer,
        policy: ConnectionPolicy?
    ): Result<ConnectionManager.Profile> = netRunCatching {
        val deviceId = peer.descriptor.deviceId

        val policy = policy ?: defaultPolicy
        val routes = selector.order(peer.routes, policy)
        if (routes.isEmpty()) throw NetworkException.NoRoute("device $deviceId has no known route")

        var lastFailure: Throwable? = null
        for (route in routes) {
            val attempt = probe(route, policy)
            attempt.getOrNull()?.let { return@netRunCatching it }
            lastFailure = attempt.exceptionOrNull()
            logger.warn(
                "route ${route.transport.value} failed for $deviceId",
                lastFailure
            )
        }

        throw lastFailure ?: NetworkException.NoRoute("device $deviceId unreachable")
    }

    /**
     * Files the handshake result under the id the peer just claimed. [PeerRef.build] dials with a
     * blank device id, so the stored route is re-stamped - otherwise nothing could dial it again.
     */
    private fun rememberProfile(route: PeerRef, link: SessionLink): ConnectionManager.Profile {
        val deviceId = link.negotiated.peer.deviceId
        val profile = ConnectionManager.Profile(
            identity = link.negotiated.peer,
            negotiated = link.negotiated,
            route = route.copy(deviceId = deviceId),
        )
        profileRegistry.update { it + (deviceId to profile) }
        return profile
    }

    suspend fun shutdown() {
        registry.value.values.forEach { it.close(CloseReason.Local("node closed")) }
        profileRegistry.update { emptyMap() }
        transports.forEach { runCatching { it.shutdown() } }
    }

    // ------------------------------------------------------------------ internals

    /** Connect to [peer] and negotiate a session link. */
    private suspend fun openLink(peer: PeerRef, policy: ConnectionPolicy): SessionLink {
        val transport = selector.forEndpoint(peer.endpoint)
            ?: throw NetworkException.NoRoute("no transport carries ${peer.endpoint.address}")

        val channel = withTimeoutOrNull(policy.connectTimeout) { transport.open(peer.endpoint) }
            ?.getOrElse {
                throw NetworkException.Transport(
                    "could not open ${peer.endpoint.address}",
                    it
                )
            }
            ?: throw NetworkException.Transport("timed out opening ${peer.endpoint.address}")

        val pump = FramePump(scope, channel)
        return try {
            negotiator.negotiate(
                pump = pump,
                role = CryptoProvider.Role.Initiator,
                capabilities = transport.capabilities,
                confirmationCode = channel.confirmationCode,
                timeout = policy.handshakeTimeout,
            )
        } catch (e: Throwable) {
            pump.close()
            throw e
        }
    }

    /** Register a new session, or return an existing one if it raced in first. */
    private suspend fun register(
        route: PeerRef,
        link: SessionLink,
        policy: ConnectionPolicy,
        relink: (suspend () -> SessionLink)?,
    ): PeerSession<M> = registryLock.withLock {
        val deviceId = link.negotiated.peer.deviceId
        // Only what this side dialed: an inbound socket's remote address is not a route back.
        if (relink != null) rememberProfile(route, link)

        registry.value[deviceId]?.let { existing ->
            val finished =
                existing.state.value.let { it is PeerSession.State.Closed || it is PeerSession.State.Failed }
            if (!finished) {
                // Raced with another caller; keep the first session and drop the spare link.
                link.secure.close()
                return@withLock existing
            }
            // A session that already died has not necessarily been unregistered yet.
            clearDeviceFromRegistry(deviceId)
        }

        if (registry.value.size >= policy.maxSessions) {
            link.secure.close()
            throw NetworkException.Transport("session limit ${policy.maxSessions} reached")
        }

        val session = PeerSessionImpl(
            route = route,
            negotiated = link.negotiated,
            codec = dictionary.codec,
            policy = policy,
            logger = logger,
            parentScope = scope,
            relink = relink,
            onTerminated = { finished -> clearDeviceFromRegistry(finished.negotiated.peer.deviceId) },
        )

        registry.update { it + (deviceId to session) }
        session.start(link)
        session
    }

    private fun clearDeviceFromRegistry(deviceId: String) {
        registry.update { it - deviceId }
        profileRegistry.update { it - deviceId }
    }

    private inner class IncomingRequest(
        private val owner: Transport,
        private val connection: Transport.InboundConnection,
    ) : ConnectionManager.IncomingRequest {
        override val transport: SpiId = connection.transport
        override val peer: DiscoveredEndpoint = connection.peer
        override val confirmationCode: String? = connection.peer.confirmationCode

        override suspend fun accept(): Result<Unit> = netRunCatching {
            val channel = connection.accept()
                .getOrElse {
                    throw NetworkException.Transport(
                        "could not accept ${peer.advertisedName}",
                        it
                    )
                }

            val pump = FramePump(scope, channel)
            val link = try {
                negotiator.negotiate(
                    pump = pump,
                    role = CryptoProvider.Role.Responder,
                    capabilities = owner.capabilities,
                    confirmationCode = confirmationCode,
                    timeout = defaultPolicy.handshakeTimeout,
                )
            } catch (e: Throwable) {
                pump.close()
                throw e
            }

            val session = register(
                route = PeerRef(link.negotiated.peer.deviceId, transport, peer.endpoint),
                link = link,
                policy = defaultPolicy,
                relink = null, // No relink: this side never dialed, so it has nothing to dial back.
            )
            logger.debug("accepted a session with ${session.route.deviceId}")
        }

        override suspend fun reject(reason: CloseReason) {
            try {
                connection.reject()
            } catch (e: Exception) {
                throw NetworkException.Transport("could not reject ${peer.advertisedName}", e)
            }
        }
    }
}
