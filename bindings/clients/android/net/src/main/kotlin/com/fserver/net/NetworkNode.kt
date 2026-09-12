package com.fserver.net

import com.fserver.net.config.NetworkConfig
import com.fserver.net.config.NetworkConfigHolder
import com.fserver.net.connection.IncomingConnectionsManager
import com.fserver.net.connection.RequestManager
import com.fserver.net.connection.impl.ConnectionsHolder
import com.fserver.net.connection.impl.IncomingConnectionsManagerImpl
import com.fserver.net.connection.impl.RequestManagerImpl
import com.fserver.net.discovery.PeerDiscovery
import com.fserver.net.discovery.PeerDiscoveryImpl
import com.fserver.net.handshake.HandshakeNegotiator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Entry point to `:net`. Build one per dictionary, keep it, [close] it when the host dies.
 *
 * The type parameter is the enforcement mechanism for "one user, one dictionary": two vocabularies
 * in one process means two nodes.
 */
@OptIn(ExperimentalAtomicApi::class)
class NetworkNode<M : Any> private constructor(
    private val configHolder: NetworkConfigHolder<M>,
    val discovery: PeerDiscovery,
    private val incomingConnectionsImpl: IncomingConnectionsManagerImpl<M>,
    private val requestManagerImpl: RequestManagerImpl<M>,
    private val ownedScope: CoroutineScope?,
) : AutoCloseable {
    private val closed = AtomicBoolean(false)

    val incoming: IncomingConnectionsManager<M> = incomingConnectionsImpl
    val requestsManager: RequestManager<M> = requestManagerImpl

    /** Current config, which may be swapped at runtime with [reloadConfig]. */
    val config: NetworkConfig<M> get() = configHolder.current

    /**
     * Swaps live config in place.
     *
     * @throws IllegalArgumentException when the new config is incompatible with old one
     */
    suspend fun reloadConfig(new: NetworkConfig<M>) {
        check(!closed.load()) { "node is closed" }

        val old = configHolder.current
        require(old.dictionary === new.dictionary) {
            "dictionary cannot change at runtime - close() this node and create() a new one"
        }
        require(old.identityStore === new.identityStore) {
            "identityStore cannot change at runtime - close() this node and create() a new one"
        }
        require(old.crypto === new.crypto) {
            "crypto provider cannot change at runtime - close() this node and create() a new one"
        }
        require(old.trustStore === new.trustStore) {
            "trustStore cannot change at runtime - close() this node and create() a new one"
        }

        configHolder.reload(fillConfig(new))
    }

    /**
     * Tears the node down without blocking. Prefer this over [close] from a coroutine: [close]
     * has to `runBlocking` its way through the same work, and holds the calling thread for up
     * to [SHUTDOWN_GRACE] doing it.
     */
    suspend fun shutdown() {
        if (!closed.compareAndSet(false, true)) return

        withTimeoutOrNull(SHUTDOWN_GRACE) {
            discovery.stopAdvertising()
            incomingConnectionsImpl.shutdown()
            requestManagerImpl.shutdown()
        }

        // Cancel scope to ensure node is fully cleaned up
        ownedScope?.cancel()
    }

    /** [shutdown] for callers with no coroutine to hand. Blocks the calling thread. */
    override fun close() {
        runBlocking { shutdown() }
    }

    companion object {
        private val SHUTDOWN_GRACE: Duration = 5.seconds

        fun <M : Any> create(config: NetworkConfig<M>): NetworkNode<M> {
            val scope = config.scope ?: CoroutineScope(SupervisorJob() + Dispatchers.IO)

            val config = fillConfig(config)
            val configHolder = NetworkConfigHolder(config)

            val negotiator = HandshakeNegotiator(configHolder = configHolder)
            val connectionsHolder = ConnectionsHolder(
                configHolder = configHolder,
                scope = scope,
            )

            val incoming = IncomingConnectionsManagerImpl(
                configHolder = configHolder,
                negotiator = negotiator,
                connectionsHolder = connectionsHolder,
                scope = scope,
            )

            val requestManager = RequestManagerImpl(
                configHolder = configHolder,
                negotiator = negotiator,
                connectionsHolder = connectionsHolder,
                scope = scope,
            )

            val discovery = PeerDiscoveryImpl(
                configHolder = configHolder,
                scope = scope,
            )

            // Registration order is matter here
            configHolder.register(connectionsHolder)
            configHolder.register(incoming)
            configHolder.register(requestManager)
            configHolder.register(discovery)

            return NetworkNode(
                configHolder = configHolder,
                discovery = discovery,
                incomingConnectionsImpl = incoming,
                requestManagerImpl = requestManager,
                ownedScope = scope.takeIf { config.scope == null },
            )
        }
    }
}

/** Check [config], print warnings, and fill in any missing defaults. */
private fun <M : Any> fillConfig(config: NetworkConfig<M>): NetworkConfig<M> {
    if (config.authenticator == null) {
        config.logger.warn("NetworkNode built without a PeerAuthenticator - every peer will be trusted")
    }

    if (config.trustStore == null) {
        config.logger.warn("NetworkNode built without a PeerTrustStore - every connection asks the user again")
    }

    if (!config.crypto.suite.isEncrypting) {
        config.logger.warn("crypto suite '${config.crypto.suite.name}' does not encrypt - frames go out in the clear")
    }

    // A transport that keys its own link admits exactly the one method it names. Substituting one
    // here would be this module deciding how a peer gets proven, which is the host's call and the
    // reason an unproven identity ever reaches a pin - so this only says what is missing.
    config.transports
        .mapNotNull { it.capabilities.security }
        .distinct()
        .filterNot { id -> config.authMethods.any { it.id == id } }
        .forEach { id ->
            config.logger.warn(
                "no auth method '$id' installed - transports that key their own link cannot authenticate"
            )
        }

    return config
}
