package com.fserver.net

import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.dictionary.MessageDictionary
import com.fserver.net.security.CryptoProvider
import com.fserver.net.security.IdentityStore
import com.fserver.net.security.PassthroughCryptoProvider
import com.fserver.net.security.PeerAuthenticator
import com.fserver.net.spi.Advertiser
import com.fserver.net.spi.DiscoveryProvider
import com.fserver.net.spi.SpiContainer
import com.fserver.net.spi.Transport
import kotlinx.coroutines.CoroutineScope

/**
 * Builds a [NetworkConfig] without the host having to keep three parallel SPI lists in sync -
 * [NetworkConfigBuilder.install] takes a whole [SpiContainer] and fans it out.
 */
fun <M : Any> networkConfig(
    dictionary: MessageDictionary<M>,
    identityStore: IdentityStore,
    block: NetworkConfigBuilder<M>.() -> Unit = {},
): NetworkConfig<M> = NetworkConfigBuilder(dictionary, identityStore).apply(block).build()

/**
 * Registration order is kept: with [ConnectionPolicy.transportOrder] left null, transports are
 * tried in the order they were installed.
 */
class NetworkConfigBuilder<M : Any>(
    private val dictionary: MessageDictionary<M>,
    private val identityStore: IdentityStore,
) {
    /** Null trusts every peer that completes a handshake - see [NetworkConfig.authenticator]. */
    var authenticator: PeerAuthenticator? = null

    /** Defaults to [PassthroughCryptoProvider], which does **not** encrypt. */
    var crypto: CryptoProvider = PassthroughCryptoProvider
    var policy: ConnectionPolicy = ConnectionPolicy()
    var logger: NetLogger = NetLogger.None

    /** Null means the node owns its scope and cancels it on [NetworkNode.close]. */
    var scope: CoroutineScope? = null

    private val transports = mutableListOf<Transport>()
    private val discoveryProviders = mutableListOf<DiscoveryProvider>()
    private val advertisers = mutableListOf<Advertiser>()
    private val advertisedAttributes = mutableMapOf<String, String>()

    /** Registers a transport with the discovery and advertiser that pair with it. */
    fun install(spi: SpiContainer) = apply {
        transport(spi.transport)
        spi.discoveryProvider?.let(::discovery)
        spi.advertiser?.let(::advertiser)
        advertise(spi.advertisedAttributes)
    }

    fun install(vararg spis: SpiContainer) = apply { spis.forEach(::install) }

    fun install(spis: Iterable<SpiContainer>) = apply { spis.forEach(::install) }

    fun transport(transport: Transport) = apply { transports += transport }

    fun discovery(provider: DiscoveryProvider) = apply { discoveryProviders += provider }

    fun advertiser(advertiser: Advertiser) = apply { advertisers += advertiser }

    /** Extra advertisement key/values; later writes win. */
    fun advertise(key: String, value: String) = apply { advertisedAttributes[key] = value }

    fun advertise(attributes: Map<String, String>) = apply { advertisedAttributes += attributes }

    fun policy(block: ConnectionPolicy.() -> ConnectionPolicy) = apply { policy = policy.block() }

    fun authenticator(authenticator: PeerAuthenticator?) = apply { this.authenticator = authenticator }

    fun crypto(crypto: CryptoProvider) = apply { this.crypto = crypto }

    fun logger(logger: NetLogger) = apply { this.logger = logger }

    fun scope(scope: CoroutineScope?) = apply { this.scope = scope }

    fun build(): NetworkConfig<M> {
        requireDistinctIds("transport", transports.map { it.id.value })
        requireDistinctIds("discovery provider", discoveryProviders.map { it.id.value })
        requireDistinctIds("advertiser", advertisers.map { it.id.value })

        return NetworkConfig(
            dictionary = dictionary,
            identityStore = identityStore,
            transports = transports.toList(),
            discoveryProviders = discoveryProviders.toList(),
            advertisers = advertisers.toList(),
            authenticator = authenticator,
            crypto = crypto,
            policy = policy,
            advertisedAttributes = advertisedAttributes.toMap(),
            logger = logger,
            scope = scope,
        )
    }

    private fun requireDistinctIds(role: String, ids: List<String>) {
        val duplicates = ids.groupingBy { it }.eachCount().filterValues { it > 1 }.keys
        require(duplicates.isEmpty()) { "duplicate $role SpiId: ${duplicates.joinToString()}" }
    }
}
