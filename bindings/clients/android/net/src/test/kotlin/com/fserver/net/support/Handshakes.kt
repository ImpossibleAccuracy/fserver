package com.fserver.net.support

import com.fserver.net.config.NetworkConfig
import com.fserver.net.config.NetworkConfigHolder
import com.fserver.net.connection.AuthConfig
import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.connection.TimeoutsConfig
import com.fserver.net.dictionary.MessageDictionary
import com.fserver.net.handshake.FramePump
import com.fserver.net.handshake.HandshakeNegotiator
import com.fserver.net.peer.PublicGreeting
import com.fserver.net.security.PeerAuthenticator
import com.fserver.net.security.auth.AuthMethod
import com.fserver.net.security.auth.AuthRequest
import com.fserver.net.security.impl.TransportConfirmationAuthMethod
import com.fserver.net.security.crypto.CryptoProvider
import com.fserver.net.security.crypto.PassthroughCryptoProvider
import com.fserver.net.security.identity.EphemeralIdentityStore
import com.fserver.net.security.trust.PeerTrustStore
import com.fserver.net.session.SessionLink
import com.fserver.net.spi.Transport
import com.fserver.net.spi.TransportCapabilities
import com.fserver.net.wire.ProtocolVersions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withTimeout
import kotlin.time.Duration.Companion.seconds

/** Short enough that a stalled test fails fast, long enough that a slow machine does not. */
internal val TEST_POLICY = ConnectionPolicy(
    timeouts = TimeoutsConfig(handshake = 5.seconds),
    authConfig = AuthConfig(authTimeout = 5.seconds),
)

/**
 * A negotiator with the node's own defaults for [AuthMethod], so tests exercise what ships rather
 * than a shortcut. Pass [authMethods] to stand something else up in their place.
 */
internal fun negotiator(
    name: String,
    dictionary: MessageDictionary<TestMessage> = TestDictionary(),
    versions: IntRange = ProtocolVersions.SUPPORTED,
    authenticator: PeerAuthenticator? = null,
    authMethods: List<AuthMethod>? = null,
    identityStore: EphemeralIdentityStore = EphemeralIdentityStore(displayName = name),
    crypto: CryptoProvider = PassthroughCryptoProvider,
    trustStore: PeerTrustStore? = null,
): HandshakeNegotiator = HandshakeNegotiator(
    configHolder = NetworkConfigHolder(
        NetworkConfig(
            dictionary = dictionary,
            identityStore = identityStore,
            authMethods = authMethods?.plus(TestingAuthMethod(crypto)) ?: listOf(
                TransportConfirmationAuthMethod(),
                TestingAuthMethod(crypto),
            ),
            authenticator = authenticator,
            trustStore = trustStore,
            crypto = crypto,
            protocolVersions = versions,
        ),
    ),
)

/** Runs both halves of one handshake at once and hands back what each of them concluded. */
internal suspend fun CoroutineScope.handshake(
    initiator: HandshakeNegotiator = negotiator("alice"),
    responder: HandshakeNegotiator = negotiator("bob"),
    initiatorCapabilities: TransportCapabilities = TransportCapabilities(),
    responderCapabilities: TransportCapabilities = TransportCapabilities(),
    initiatorConfirmationCode: String? = null,
    responderConfirmationCode: String? = null,
    policy: ConnectionPolicy = TEST_POLICY,
    request: AuthRequest? = null,
): Pair<Result<SessionLink>, Result<SessionLink>> {
    val (initiatorChannel, responderChannel) = channelPair()

    val initiatorSide = negotiate(
        initiator,
        initiatorChannel,
        CryptoProvider.Role.Initiator,
        initiatorCapabilities,
        initiatorConfirmationCode,
        policy,
        request,
    )
    val responderSide = negotiate(
        responder,
        responderChannel,
        CryptoProvider.Role.Responder,
        responderCapabilities,
        responderConfirmationCode,
        policy,
    )

    return withTimeout(AWAIT) { initiatorSide.await() to responderSide.await() }
}

/** One side of a handshake, taken all the way. The responder accepts whatever asks. */
internal fun CoroutineScope.negotiate(
    negotiator: HandshakeNegotiator,
    channel: Transport.Channel,
    role: CryptoProvider.Role,
    capabilities: TransportCapabilities = TransportCapabilities(),
    confirmationCode: String? = null,
    policy: ConnectionPolicy = TEST_POLICY,
    request: AuthRequest? = null,
): Deferred<Result<SessionLink>> = async {
    val pump = FramePump(this@negotiate, channel)
    runCatching {
        when (role) {
            CryptoProvider.Role.Initiator ->
                negotiator.connect(pump, capabilities, confirmationCode, policy, request)

            CryptoProvider.Role.Responder ->
                negotiator.receive(pump, capabilities, policy).accept(confirmationCode, policy)
        }
    }
}

/** The public half alone, as a probe runs it. */
internal fun CoroutineScope.greet(
    negotiator: HandshakeNegotiator,
    channel: Transport.Channel,
    capabilities: TransportCapabilities = TransportCapabilities(),
    policy: ConnectionPolicy = TEST_POLICY,
): Deferred<Result<PublicGreeting>> = async {
    runCatching { negotiator.greet(FramePump(this@greet, channel), capabilities, policy) }
}

private val AWAIT = 10.seconds
