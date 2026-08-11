package com.fserver.net

import com.fserver.net.config.NetworkConfig
import com.fserver.net.connection.ConnectionManager
import com.fserver.net.connection.ConnectionManagerImpl
import com.fserver.net.discovery.PeerDiscovery
import com.fserver.net.discovery.PeerDiscoveryImpl
import com.fserver.net.security.LocalIdentity
import com.fserver.net.wire.ProtocolVersions
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
    val connections: ConnectionManager<M>,
    private val connectionsImpl: ConnectionManagerImpl<M>,
    /** Non-null only when the node created the scope, and so is the one allowed to cancel it. */
    private val ownedScope: CoroutineScope?,
) : AutoCloseable {

    override fun close() {
        // Bounded: transport that will not shut down must not wedge the host's teardown.
        runBlocking { withTimeoutOrNull(SHUTDOWN_GRACE) { connectionsImpl.shutdown() } }
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

            val connections = ConnectionManagerImpl(
                transports = config.transports,
                dictionary = config.dictionary,
                identityStore = config.identityStore,
                crypto = config.crypto,
                authenticator = config.authenticator,
                defaultPolicy = config.policy,
                protocolVersions = ProtocolVersions.SUPPORTED,
                logger = config.logger,
                scope = scope,
            )

            val discovery = PeerDiscoveryImpl(
                providers = config.discoveryProviders,
                advertisers = config.advertisers,
                identityStore = config.identityStore,
                dictionary = config.dictionary.descriptor,
                advertisedAttributes = config.advertisedAttributes,
                logger = config.logger,
                scope = scope,
            )

            return NetworkNode(
                identity = config.identityStore.local,
                discovery = discovery,
                connections = connections,
                connectionsImpl = connections,
                ownedScope = scope.takeIf { config.scope == null },
            )
        }
    }
}
