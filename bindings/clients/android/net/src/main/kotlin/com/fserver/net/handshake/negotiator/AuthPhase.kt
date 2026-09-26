package com.fserver.net.handshake.negotiator

import com.fserver.common.exception.NetworkException
import com.fserver.net.config.NetworkConfig
import com.fserver.net.config.NetworkConfigHolder
import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.security.auth.AuthContext
import com.fserver.net.security.auth.AuthMethod
import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.security.auth.AuthRequest
import com.fserver.net.security.auth.HandshakeIo
import com.fserver.net.security.auth.IdentityExchange
import com.fserver.net.security.auth.KnownPeerExchange
import com.fserver.net.security.auth.offeredMethods
import com.fserver.net.security.auth.runnableMethods
import com.fserver.net.security.crypto.CryptoProvider
import com.fserver.net.security.identity.LocalIdentity
import com.fserver.net.security.identity.PeerIdentity
import com.fserver.net.security.trust.TrustGate
import com.fserver.net.spi.TransportCapabilities
import com.fserver.net.wire.ByteWriter
import com.fserver.net.wire.FrameKind
import kotlin.time.Duration

/** Method selection and the `AUTH` round trip. */
internal class AuthPhase(
    private val configHolder: NetworkConfigHolder<*>,
) {
    private val config: NetworkConfig<*> get() = configHolder.current

    fun offered(capabilities: TransportCapabilities): List<AuthMethod> =
        config.offeredMethods(capabilities)

    fun assertLocallyOffered(id: AuthMethodId, capabilities: TransportCapabilities): AuthMethod =
        offered(capabilities).firstOrNull { it.id == id }
            ?: throw NetworkException.Handshake("auth method $id is not offered on this transport")

    /**
     * The method to run. With [requested] set the caller has already put a prompt in front of the
     * user for that one, so anything else is a downgrade and ends the attempt; without it, the
     * first of the peer's offers this side will also run, in the peer's order.
     */
    fun chooseMethod(
        remote: List<AuthMethodId>,
        capabilities: TransportCapabilities,
        requested: AuthMethodId?,
    ): AuthMethod {
        if (requested != null) {
            // Whether this side offers the method is beside the point: the peer is the one answering.
            return config.runnableMethods(capabilities)
                .firstOrNull { it.id == requested && requested in remote }
                ?: throw NetworkException.Handshake(
                    "auth method $requested was asked for but is not on offer: peer offers $remote"
                )
        }
        val local = offered(capabilities)
        return remote.firstNotNullOfOrNull { id -> local.firstOrNull { it.id == id } }
            ?: throw NetworkException.Handshake(
                "no common auth method: peer offers $remote, this side offers ${local.map { it.id }}"
            )
    }

    /**
     * The chosen method, then the part no method is trusted to do for itself: state this device's
     * identity and prove it, check the peer's, and put what was proven in front of the gate. Only
     * a peer that got through all of it reaches the method's own confirmation exchange.
     */
    suspend fun run(
        identity: LocalIdentity,
        wire: Wire,
        method: AuthMethod,
        role: CryptoProvider.Role,
        request: AuthRequest?,
        prologue: ByteArray,
        confirmationCode: String?,
        policy: ConnectionPolicy,
        firstIncoming: ByteArray?,
        trust: TrustGate.Session,
    ): AuthResult {
        val io = AuthIo(
            wire = wire,
            // Only the side that picked the method announces it, and only on its first frame.
            methodId = method.id.takeIf { firstIncoming == null },
            pending = firstIncoming,
            timeout = policy.authConfig.authTimeout,
            maxRounds = policy.authConfig.maxAuthRounds,
        )
        val context = AuthContext(
            role = role,
            request = request,
            prologue = prologue,
            confirmationCode = confirmationCode,
            local = identity,
        )
        return wire.guarded {
            val outcome = method.run(io, context)
            val peer = IdentityExchange.run(io, context, outcome, config.identityStore)

            // A method pointed at one device out of band gets to say whether this is that device:
            // whoever answered may be perfectly genuine and still not be the one that was scanned.
            outcome.verifyPeer(peer)

            val peerKnowsUs = KnownPeerExchange.run(io, outcome.aead, trust.knows(peer))

            trust.check(
                peer = peer,
                confirmationCode = outcome.confirmationCode,
                peerKnowsUs = peerKnowsUs,
                keyVerifiedOutOfBand = !outcome.needVerifyKey,
            )
            outcome.confirm()

            AuthResult(
                sharedSecret = outcome.sharedSecret(),
                peer = peer,
                peerKnowsUs = peerKnowsUs,
            )
        }
    }

    /** Carries `AUTH` payloads for one method run, and stops it running forever. */
    private class AuthIo(
        private val wire: Wire,
        private val methodId: AuthMethodId?,
        private var pending: ByteArray?,
        private val timeout: Duration,
        private val maxRounds: Int,
    ) : HandshakeIo {
        private var frames = 0
        private var firstSend = true

        override suspend fun send(payload: ByteArray) {
            count()
            val body = if (firstSend && methodId != null) {
                ByteWriter(payload.size + METHOD_ID_HEADROOM)
                    .string(methodId.value)
                    .bytes(payload)
                    .toByteArray()
            } else {
                payload
            }
            firstSend = false
            wire.write(FrameKind.AUTH, body)
        }

        override suspend fun receive(): ByteArray {
            // The responder's first payload was read to get the method id out of it.
            pending?.let { pending = null; return it }
            count()
            return wire.expect(FrameKind.AUTH, timeout).payload
        }

        private fun count() {
            if (++frames > maxRounds * 2) {
                throw NetworkException.Handshake("auth method $methodId ran past $maxRounds rounds")
            }
        }
    }

    private companion object {
        /** Room for the method id a first AUTH frame carries ahead of the method's own payload. */
        const val METHOD_ID_HEADROOM = 64
    }
}

/** What the whole `AUTH` phase concluded: the key the session runs on, and who is on the far end. */
internal class AuthResult(
    val sharedSecret: ByteArray,
    val peer: PeerIdentity,
    /** What [peer] said about us - see [KnownPeerExchange] for how much that is worth. */
    val peerKnowsUs: Boolean,
)
