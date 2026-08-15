package com.fserver.net

import com.fserver.net.config.NetworkConfig
import com.fserver.net.connection.IncomingConnectionsManager
import com.fserver.net.connection.RequestManager
import com.fserver.net.connection.impl.ConnectionsHolder
import com.fserver.net.connection.impl.IncomingConnectionsManagerImpl
import com.fserver.net.connection.impl.RequestManagerImpl
import com.fserver.net.discovery.PeerDiscovery
import com.fserver.net.discovery.PeerDiscoveryImpl
import com.fserver.net.handshake.HandshakeNegotiator
import com.fserver.net.security.auth.AuthMethod
import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.security.auth.TransportConfirmationAuthMethod
import com.fserver.net.security.identity.LocalIdentity
import com.fserver.net.spi.Transport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Entry point to `:net`. Build one per dictionary, keep it, [close] it when the host dies.
 *
 * The type parameter is the enforcement mechanism for "one user, one dictionary": two vocabularies
 * in one process means two nodes.
 */
class NetworkNode<M : Any> private constructor(
    val identity: LocalIdentity,
    val discovery: PeerDiscovery,
    private val incomingConnectionsImpl: IncomingConnectionsManagerImpl<M>,
    private val requestManagerImpl: RequestManagerImpl<M>,
    /** Non-null only when the node created the scope, and so is the one allowed to cancel it. */
    private val ownedScope: CoroutineScope?,
) : AutoCloseable {
    val incoming: IncomingConnectionsManager<M> = incomingConnectionsImpl
    val requestsManager: RequestManager<M> = requestManagerImpl

    override fun close() {
        // Bounded: transport that will not shut down must not wedge the host's teardown.
        runBlocking {
            withTimeoutOrNull(SHUTDOWN_GRACE) {
                incomingConnectionsImpl.shutdown()
                requestManagerImpl.shutdown()
            }
        }
        discovery.stopAdvertising()
        ownedScope?.cancel()
    }

    companion object {
        private val SHUTDOWN_GRACE: Duration = 5.seconds

        fun <M : Any> create(config: NetworkConfig<M>): NetworkNode<M> {
            val scope = config.scope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)

            if (config.authenticator == null) {
                config.logger.warn("NetworkNode built without a PeerAuthenticator - every peer will be trusted")
            }
            if (!config.crypto.suite.isEncrypting) {
                config.logger.warn("crypto suite '${config.crypto.suite.name}' does not encrypt - frames go out in the clear")
            }

            val authMethods = config.authMethods.plus(TransportConfirmationAuthMethod(config.authenticator))
            val config = config.copy(authMethods = authMethods)

            val negotiator = HandshakeNegotiator(config = config)
            val connectionsHolder = ConnectionsHolder(
                dictionary = config.dictionary,
                logger = config.logger,
                scope = scope,
            )

            val incoming = IncomingConnectionsManagerImpl(
                config = config,
                negotiator = negotiator,
                connectionsHolder = connectionsHolder,
                scope = scope,
            )

            val requestManager = RequestManagerImpl(
                config = config,
                negotiator = negotiator,
                connectionsHolder = connectionsHolder,
                scope = scope,
            )

            val discovery = PeerDiscoveryImpl(
                providers = config.discoveryProviders,
                advertisers = config.advertisers,
                identityStore = config.identityStore,
                policy = config.advertisement,
                authMethods = advertisableMethods(authMethods, config.transports),
                advertisedAttributes = config.advertisedAttributes,
                logger = config.logger,
                scope = scope,
            )

            return NetworkNode(
                identity = config.identityStore.local,
                discovery = discovery,
                incomingConnectionsImpl = incoming,
                requestManagerImpl = requestManager,
                ownedScope = scope.takeIf { config.scope == null },
            )
        }

        /**
         * Which methods are worth putting on the air. A transport-backed one is announced only if
         * some installed transport actually backs it - promising `nearby-sas` over Wi-Fi would be
         * a claim this node cannot honour.
         */
        private fun advertisableMethods(
            methods: List<AuthMethod>,
            transports: List<Transport>,
        ): List<AuthMethodId> {
            val backed = transports
                .mapNotNull { it.capabilities.security }
                .toSet()
            return methods.filter { !it.requiresChannelSecurity || it.id in backed }.map { it.id }
        }
    }
}
