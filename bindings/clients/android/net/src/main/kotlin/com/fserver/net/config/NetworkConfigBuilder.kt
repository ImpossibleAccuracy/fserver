package com.fserver.net.config

import com.fserver.net.NetLogger
import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.dictionary.MessageDictionary
import com.fserver.net.security.CryptoProvider
import com.fserver.net.security.IdentityStore
import com.fserver.net.security.PassthroughCryptoProvider
import com.fserver.net.security.PeerAuthenticator
import kotlinx.coroutines.CoroutineScope

/**
 * Assembles a [NetworkConfig] out of [SpiFactory] installs.
 *
 * Settings are read at [build], not at [install], so the order of calls does not matter: a factory
 * always sees the final [policy], [logger] and identity store.
 */
class NetworkConfigBuilder<T : Any>(
    private val dictionary: MessageDictionary<T>,
) {
    /** No default is possible - a node with no identity has nothing to advertise or authenticate. */
    var identityStore: IdentityStore? = null
    var authenticator: PeerAuthenticator? = null
    var crypto: CryptoProvider = PassthroughCryptoProvider
    var policy: ConnectionPolicy = ConnectionPolicy()
    var logger: NetLogger = NetLogger.None
    var scope: CoroutineScope? = null

    private val factories = mutableListOf<SpiFactory>()
    private val attributes = mutableMapOf<String, String>()

    fun install(vararg factories: SpiFactory): NetworkConfigBuilder<T> = apply {
        this.factories += factories
    }

    fun install(factories: Iterable<SpiFactory>): NetworkConfigBuilder<T> = apply {
        this.factories += factories
    }

    /** For an SPI that is already built - a test double, mostly. */
    fun install(container: SpiContainer): NetworkConfigBuilder<T> = install({ container })

    /** Advertised over whatever every installed SPI contributes; a repeated key wins here. */
    fun advertise(key: String, value: String): NetworkConfigBuilder<T> = apply {
        attributes[key] = value
    }

    fun advertise(attributes: Map<String, String>): NetworkConfigBuilder<T> = apply {
        this.attributes += attributes
    }

    fun build(): NetworkConfig<T> {
        val identityStore = checkNotNull(identityStore) {
            "identityStore is required: set it before build()"
        }

        val environment = SpiEnvironment(
            identityStore = identityStore,
            policy = policy,
            logger = logger,
        )
        val containers = factories.map { it.create(environment) }

        val duplicates = containers.groupBy { it.transport.id }.filterValues { it.size > 1 }.keys
        require(duplicates.isEmpty()) {
            "transport installed more than once: ${duplicates.joinToString { it.value }}"
        }

        return NetworkConfig(
            dictionary = dictionary,
            identityStore = identityStore,
            transports = containers.map { it.transport },
            discoveryProviders = containers.mapNotNull { it.discoveryProvider },
            advertisers = containers.mapNotNull { it.advertiser },
            authenticator = authenticator,
            crypto = crypto,
            policy = policy,
            advertisedAttributes = containers.fold(emptyMap<String, String>()) { acc, container ->
                acc + container.advertisedAttributes
            } + attributes,
            logger = logger,
            scope = scope,
        )
    }
}

fun <T : Any> networkConfig(
    dictionary: MessageDictionary<T>,
    block: NetworkConfigBuilder<T>.() -> Unit,
): NetworkConfig<T> = NetworkConfigBuilder(dictionary).apply(block).build()
