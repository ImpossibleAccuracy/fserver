package com.fserver.net.config

import com.fserver.net.NetLogger
import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.dictionary.MessageDictionary
import com.fserver.net.security.PeerAuthenticator
import com.fserver.net.security.auth.AuthMethod
import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.security.crypto.CryptoProvider
import com.fserver.net.security.crypto.PassthroughCryptoProvider
import com.fserver.net.security.identity.IdentityStore
import com.fserver.net.security.trust.PeerTrustStore
import com.fserver.net.spi.Advertiser
import com.fserver.net.spi.DiscoveryProvider
import com.fserver.net.spi.Transport
import com.fserver.net.wire.ProtocolVersions
import kotlinx.coroutines.CoroutineScope

/**
 * Everything a [com.fserver.net.NetworkNode] needs. Read once, at construction.
 */
data class NetworkConfig<T : Any>(
    // user config section
    val dictionary: MessageDictionary<T>,
    val identityStore: IdentityStore,
    val policy: ConnectionPolicy = ConnectionPolicy(),

    // SPI section
    val transports: List<Transport> = emptyList(),
    val discoveryProviders: List<DiscoveryProvider> = emptyList(),
    val advertisers: List<Advertiser> = emptyList(),
    val advertisedAttributes: Map<String, String> = emptyMap(),

    // security section
    val authenticator: PeerAuthenticator? = null,
    /** Peers pinned by earlier handshakes. Absent means nothing is remembered between them. */
    val trustStore: PeerTrustStore? = null,
    /** Every method this node can run, whether dialling or answering. */
    val authMethods: List<AuthMethod> = emptyList(),
    /** Which of [authMethods] peers may use to reach this node; null offers all of them. */
    val offeredMethodIds: Set<AuthMethodId>? = null,
    val crypto: CryptoProvider = PassthroughCryptoProvider,

    // everything else
    val logger: NetLogger = NetLogger.None,
    val scope: CoroutineScope? = null,
    internal val protocolVersions: IntRange = ProtocolVersions.SUPPORTED,
)
