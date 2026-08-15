package com.fserver.net.config

import com.fserver.net.NetLogger
import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.security.PeerAuthenticator
import com.fserver.net.security.crypto.CryptoProvider
import com.fserver.net.security.identity.IdentityStore

/**
 * Builds one SPI's parts once the node-wide settings are known.
 *
 * The point of the indirection: an implementation takes only what is its own - a platform handle
 * and its own config - at construction, and gets everything shared through [NodeConfigEnvironment] at
 * [create]. So no factory has to be handed the identity store or the policy by the caller, and no
 * caller can hand it a different one than the node ends up using.
 */
fun interface SpiFactory {
    fun create(environment: NodeConfigEnvironment): SpiContainer
}

/**
 * Node-wide settings an SPI may need, as they will be in the built [NetworkConfig]. Handed to
 * [SpiFactory.create] by [NetworkConfigBuilder] - nothing else constructs one.
 */
class NodeConfigEnvironment internal constructor(
    val identityStore: IdentityStore,
    val policy: ConnectionPolicy,
    val crypto: CryptoProvider,
    val authenticator: PeerAuthenticator?,
    val logger: NetLogger,
)
