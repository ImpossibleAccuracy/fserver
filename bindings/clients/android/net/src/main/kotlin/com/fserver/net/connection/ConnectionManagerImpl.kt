package com.fserver.net.connection

import com.fserver.net.NetLogger
import com.fserver.net.NoRouteException
import com.fserver.net.TransportException
import com.fserver.net.dictionary.MessageDictionary
import com.fserver.net.discovery.DiscoveredPeer
import com.fserver.net.handshake.FramePump
import com.fserver.net.handshake.HandshakeNegotiator
import com.fserver.net.security.CryptoProvider
import com.fserver.net.security.HandshakeRole
import com.fserver.net.security.IdentityStore
import com.fserver.net.security.PeerAuthenticator
import com.fserver.net.session.CloseReason
import com.fserver.net.session.PeerSession
import com.fserver.net.session.PeerSessionImpl
import com.fserver.net.session.SessionLink
import com.fserver.net.session.SessionState
import com.fserver.net.spi.DiscoveredEndpoint
import com.fserver.net.spi.InboundConnection
import com.fserver.net.spi.Transport
import com.fserver.net.spi.TransportCapabilities
import com.fserver.net.spi.TransportId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull

internal class ConnectionManagerImpl<M : Any>(
    private val transports: List<Transport>,
    private val dictionary: MessageDictionary<M>,
    identityStore: IdentityStore,
    crypto: CryptoProvider,
    authenticator: PeerAuthenticator?,
    private val defaultPolicy: ConnectionPolicy,
    protocolVersions: IntRange,
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

    override val incoming: Flow<IncomingConnectionRequest> =
        transports.mapNotNull { transport ->
            transport.listener?.listen()?.map { connection ->
                IncomingRequest(transport, connection) as IncomingConnectionRequest
            }
        }.merge()

    override suspend fun connect(peer: PeerRef, policy: ConnectionPolicy?): Result<PeerSession<M>> =
        netRunCatching {
            session(peer.deviceId)?.let { return@netRunCatching it }

            val effective = policy ?: defaultPolicy
            val link = openLink(peer, effective)
            register(peer, link, effective) { openLink(peer, effective) }
        }

    override suspend fun connect(peer: DiscoveredPeer, policy: ConnectionPolicy?): Result<PeerSession<M>> =
        netRunCatching {
            session(peer.deviceId)?.let { return@netRunCatching it }

            val effective = policy ?: defaultPolicy
            val routes = selector.order(peer.routes, effective)
            if (routes.isEmpty()) throw NoRouteException("device ${peer.deviceId} has no known route")

            var lastFailure: Throwable? = null
            for (route in routes) {
                val attempt = connect(route, effective)
                attempt.getOrNull()?.let { return@netRunCatching it }
                lastFailure = attempt.exceptionOrNull()
                logger.warn("route ${route.transport.value} failed for ${peer.deviceId}", lastFailure)
            }

            throw lastFailure ?: NoRouteException("device ${peer.deviceId} unreachable")
        }

    override fun session(deviceId: String): PeerSession<M>? = registry.value[deviceId]

    override suspend fun disconnect(deviceId: String, reason: CloseReason) {
        registry.value[deviceId]?.close(reason)
    }

    override suspend fun probe(peer: PeerRef, policy: ConnectionPolicy?): Result<PeerProfile> =
        netRunCatching {
            val link = openLink(peer, policy ?: defaultPolicy)
            try {
                PeerProfile(
                    identity = link.negotiated.peer,
                    negotiated = link.negotiated,
                    route = peer,
                )
            } finally {
                link.secure.close()
            }
        }

    suspend fun shutdown() {
        registry.value.values.forEach { it.close(CloseReason.Local("node closed")) }
        transports.forEach { runCatching { it.shutdown() } }
    }

    // ------------------------------------------------------------------ internals

    private suspend fun openLink(peer: PeerRef, policy: ConnectionPolicy): SessionLink {
        val transport = selector.forEndpoint(peer.endpoint)
            ?: throw NoRouteException("no transport carries ${peer.endpoint.address}")

        val channel = withTimeoutOrNull(policy.connectTimeout) { transport.open(peer.endpoint) }
            ?.getOrElse { throw TransportException("could not open ${peer.endpoint.address}", it) }
            ?: throw TransportException("timed out opening ${peer.endpoint.address}")

        val pump = FramePump(scope, channel)
        return try {
            negotiator.negotiate(
                pump = pump,
                role = HandshakeRole.Initiator,
                capabilities = transport.capabilities,
                confirmationCode = null,
                timeout = policy.handshakeTimeout,
            )
        } catch (e: Throwable) {
            pump.close()
            throw e
        }
    }

    private suspend fun register(
        route: PeerRef,
        link: SessionLink,
        policy: ConnectionPolicy,
        relink: (suspend () -> SessionLink)?,
    ): PeerSession<M> = registryLock.withLock {
        val deviceId = link.negotiated.peer.deviceId

        registry.value[deviceId]?.let { existing ->
            val finished = existing.state.value.let { it is SessionState.Closed || it is SessionState.Failed }
            if (!finished) {
                // Raced with another caller; keep the first session and drop the spare link.
                link.secure.close()
                return@withLock existing
            }
            // A session that already died has not necessarily been unregistered yet.
            registry.update { it - deviceId }
        }

        if (registry.value.size >= policy.maxSessions) {
            link.secure.close()
            throw TransportException("session limit ${policy.maxSessions} reached")
        }

        val session = PeerSessionImpl(
            peer = link.negotiated.peer,
            transport = route.transport,
            codec = dictionary.codec,
            policy = policy,
            logger = logger,
            parentScope = scope,
            relink = relink,
            onTerminated = { finished -> registry.update { it - finished.peer.deviceId } },
        )

        registry.update { it + (deviceId to session) }
        session.start(link)
        session
    }

    private inner class IncomingRequest(
        private val owner: Transport,
        private val connection: InboundConnection,
    ) : IncomingConnectionRequest {
        override val transport: TransportId = connection.transport
        override val peer: DiscoveredEndpoint = connection.peer
        override val confirmationCode: String? = connection.peer.confirmationCode

        override suspend fun accept(): Result<Unit> = netRunCatching {
            val channel = connection.accept()
                .getOrElse { throw TransportException("could not accept ${peer.advertisedName}", it) }

            val pump = FramePump(scope, channel)
            val link = try {
                negotiator.negotiate(
                    pump = pump,
                    role = HandshakeRole.Responder,
                    capabilities = owner.capabilities,
                    confirmationCode = confirmationCode,
                    timeout = defaultPolicy.handshakeTimeout,
                )
            } catch (e: Throwable) {
                pump.close()
                throw e
            }

            // No relink: this side never dialled, so it has nothing to dial back.
            val session = register(
                route = PeerRef(link.negotiated.peer.deviceId, transport, peer.endpoint),
                link = link,
                policy = defaultPolicy,
                relink = null,
            )
            logger.debug("accepted a session with ${session.peer.deviceId}")
        }

        override suspend fun reject(reason: CloseReason) {
            runCatching { connection.reject() }
        }
    }
}

/** [runCatching] that still lets structured cancellation through. */
internal inline fun <T> netRunCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
