package com.fserver.net.handshake

import com.fserver.net.config.NetworkConfig
import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.handshake.negotiator.AuthPhase
import com.fserver.net.handshake.negotiator.PublicHalfResult
import com.fserver.net.handshake.negotiator.PublicPhase
import com.fserver.net.handshake.negotiator.SealedPhase
import com.fserver.net.handshake.negotiator.Wire
import com.fserver.net.peer.PublicGreeting
import com.fserver.net.security.NegotiatedParameters
import com.fserver.net.security.SecureChannel
import com.fserver.net.security.auth.AuthMethod
import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.security.auth.AuthRequest
import com.fserver.net.security.crypto.CryptoProvider
import com.fserver.net.session.SessionLink
import com.fserver.net.spi.TransportCapabilities
import com.fserver.net.wire.ByteReader
import com.fserver.net.wire.FrameKind

/**
 * Runs the handshake over a freshly opened channel and hands back a sealed link.
 *
 * Two halves, split by what they may reveal:
 *
 * ```
 * HELLO / HELLO_ACK   public, in the clear - versions and auth methods, nothing else
 * AUTH × N            the chosen method, payload opaque to this class; identities travel here
 * ─── sealed ───
 * DESCRIPTOR          name, kind, dictionary; both sides send, neither waits its turn
 * READY
 * ```
 *
 * A [greet] stops after the first line and hands back what a stranger is allowed to know. Both
 * hellos are folded into the method's key as a prologue, so tampering with what the two sides
 * agreed on out in the open changes the key and the session simply fails to come up. There is no
 * separate comparison step that could be forgotten.
 *
 * The handshake runs unconditionally, including on transports that encrypt themselves: Nearby
 * protects the link but proves nothing about *which* device is on it. What its [ChannelSecurity]
 * declaration does decide is which [AuthMethod] may run.
 *
 * The three rows above map to three collaborators under `handshake/negotiator/` -
 * [PublicPhase], [AuthPhase], [SealedPhase] - each owning its frames and nothing else. This class
 * only sequences them and owns the entry points ([greet], [connect], [receive]) that the rest of
 * `:net` calls.
 */
internal class HandshakeNegotiator(
    private val config: NetworkConfig<*>,
) {
    private val authPhase = AuthPhase(config)
    private val publicPhase = PublicPhase(config, authPhase)
    private val sealedPhase = SealedPhase(config)

    /**
     * The public half alone. Asks nothing of the user on either end, leaves no state behind, and
     * the caller closes the link straight after.
     */
    suspend fun greet(
        pump: FramePump,
        capabilities: TransportCapabilities,
        policy: ConnectionPolicy,
    ): PublicGreeting = publicPhase.initiate(
        wire = Wire(send = pump::send, next = pump::next),
        capabilities = capabilities,
        policy = policy,
    ).greeting()

    /** Dial: the whole handshake, ending in a session link. */
    suspend fun connect(
        pump: FramePump,
        capabilities: TransportCapabilities,
        confirmationCode: String?,
        policy: ConnectionPolicy,
        request: AuthRequest?,
    ): SessionLink {
        val wire = Wire(send = pump::send, next = pump::next)
        val half = publicPhase.initiate(
            wire = wire,
            capabilities = capabilities,
            policy = policy,
        )

        val method = wire.guarded {
            authPhase.chooseMethod(
                remote = half.remote.methods,
                capabilities = capabilities,
                requested = request?.method,
            )
        }

        return finish(
            wire = wire,
            pump = pump,
            role = CryptoProvider.Role.Initiator,
            half = half,
            method = method,
            firstAuthPayload = null,
            confirmationCode = confirmationCode,
            capabilities = capabilities,
            policy = policy,
        )
    }

    /**
     * Answer: run the public half, then wait for the peer to actually ask to authenticate.
     *
     * That wait is what separates a probe from a connection attempt. A peer that only wanted the
     * greeting hangs up here and this throws, so nothing is ever put in front of the user for it.
     */
    suspend fun receive(
        pump: FramePump,
        capabilities: TransportCapabilities,
        policy: ConnectionPolicy,
    ): Inbound {
        val wire = Wire(pump::send, pump::next)
        val half = publicPhase.respond(wire, capabilities, policy)

        // The initiator names its choice in the first AUTH frame. Whether that choice is allowed
        // here is decided against this side's own transport, never against the claim itself.
        val first = wire.expect(FrameKind.AUTH, policy.authConfig.authTimeout)
        val reader = ByteReader(first.payload)
        val chosen = AuthMethodId(reader.string())
        val firstPayload = reader.bytes()
        val method = wire.guarded { authPhase.assertLocallyOffered(chosen, capabilities) }

        return Inbound(
            wire = wire,
            pump = pump,
            half = half,
            method = method,
            firstAuthPayload = firstPayload,
            capabilities = capabilities,
        )
    }

    private suspend fun finish(
        wire: Wire,
        pump: FramePump,
        role: CryptoProvider.Role,
        half: PublicHalfResult,
        method: AuthMethod,
        firstAuthPayload: ByteArray?,
        confirmationCode: String?,
        capabilities: TransportCapabilities,
        policy: ConnectionPolicy,
    ): SessionLink {
        val outcome = authPhase.run(
            wire = wire,
            method = method,
            role = role,
            prologue = half.prologue,
            confirmationCode = confirmationCode,
            policy = policy,
            firstIncoming = firstAuthPayload,
        )

        val secure = SecureChannel(
            reader = pump::next,
            remainingFrames = pump::remaining,
            writer = pump::send,
            closer = pump::close,
            aead = config.crypto.aead(outcome.sharedSecret, role),
        )
        val sealed = Wire(secure::send, secure::next)

        // Nothing below this line is visible to anyone who merely reached the address.
        val peerDescriptor = sealedPhase.exchangeDescriptors(sealed, capabilities, policy)
        val dictionaryVersion = sealedPhase.negotiateDictionary(sealed, peerDescriptor.dictionary)
        sealedPhase.confirmReady(sealed, role, policy)

        return SessionLink(
            secure = secure,
            negotiated = NegotiatedParameters(
                protocolVersion = half.protocolVersion,
                dictionaryVersion = dictionaryVersion,
                cipherSuite = config.crypto.suite,
                maxFrameSize = minOf(capabilities.maxFrameSize, peerDescriptor.maxFrameSize),
                peer = outcome.peer,
                peerDescriptor = peerDescriptor,
            ),
        )
    }

    /** A peer that has asked to authenticate and is waiting to hear whether this side will. */
    inner class Inbound internal constructor(
        private val wire: Wire,
        private val pump: FramePump,
        private val half: PublicHalfResult,
        private val method: AuthMethod,
        private val firstAuthPayload: ByteArray,
        private val capabilities: TransportCapabilities,
    ) {
        val greeting: PublicGreeting = half.greeting()
        val chosenMethod: AuthMethodId = method.id

        suspend fun accept(confirmationCode: String?, policy: ConnectionPolicy): SessionLink =
            finish(
                wire = wire,
                pump = pump,
                role = CryptoProvider.Role.Responder,
                half = half,
                method = method,
                firstAuthPayload = firstAuthPayload,
                confirmationCode = confirmationCode,
                capabilities = capabilities,
                policy = policy,
            )

        suspend fun reject(reason: String) {
            wire.sendClose(reason)
            pump.close()
        }
    }
}
