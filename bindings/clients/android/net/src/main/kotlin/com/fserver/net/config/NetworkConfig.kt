package com.fserver.net.config

import com.fserver.net.NetLogger
import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.dictionary.MessageDictionary
import com.fserver.net.security.PeerAuthenticator
import com.fserver.net.security.auth.AuthMethod
import com.fserver.net.security.crypto.CryptoProvider
import com.fserver.net.security.crypto.PassthroughCryptoProvider
import com.fserver.net.security.identity.IdentityStore
import com.fserver.net.spi.Advertiser
import com.fserver.net.spi.DiscoveryProvider
import com.fserver.net.spi.Transport
import com.fserver.net.wire.ProtocolVersions
import kotlinx.coroutines.CoroutineScope

/**
 * Everything a [com.fserver.net.NetworkNode] needs. Read once, at construction.
 *
 * @property dictionary the whole reason this module is generic: one user, one dictionary.
 * @property authenticator optional. Leaving it null trusts every peer that completes a
 * handshake - fine for a test rig, wrong for a shipping client.
 * @property authMethods ways this node is willing to authenticate a peer.
 * @property crypto defaults to [PassthroughCryptoProvider], which does **not** encrypt.
 * @property advertisement what this device announces about itself, and whether it announces at
 * all.
 * @property advertisedAttributes extra key/values to put in the advertisement, merged over the
 * ones `:net` fills in.
 * @property scope work that must outlive a caller; null means the node owns one and cancels it
 * on [com.fserver.net.NetworkNode.close].
 *
 * Note what is *not* here: anything about OS permissions. Whether a radio is on or a permission
 * granted is the host's business, checked before it calls in.
 */
data class NetworkConfig<T : Any>(
    val dictionary: MessageDictionary<T>,
    val identityStore: IdentityStore,
    val transports: List<Transport> = emptyList(),
    val discoveryProviders: List<DiscoveryProvider> = emptyList(),
    val advertisers: List<Advertiser> = emptyList(),
    val authenticator: PeerAuthenticator? = null,
    val authMethods: List<AuthMethod> = emptyList(),
    val crypto: CryptoProvider = PassthroughCryptoProvider,
    val policy: ConnectionPolicy = ConnectionPolicy(),
    val advertisement: AdvertisementPolicy = AdvertisementPolicy(),
    val advertisedAttributes: Map<String, String> = emptyMap(),
    val logger: NetLogger = NetLogger.None,
    val scope: CoroutineScope? = null,
    val protocolVersions: IntRange = ProtocolVersions.SUPPORTED,
)
