package com.fserver.net.handshake

import com.fserver.net.AuthenticationRejectedException
import com.fserver.net.DictionaryMismatchException
import com.fserver.net.HandshakeException
import com.fserver.net.NetLogger
import com.fserver.net.dictionary.DictionaryDecision
import com.fserver.net.dictionary.MessageDictionary
import com.fserver.net.security.AuthDecision
import com.fserver.net.security.CryptoProvider
import com.fserver.net.security.HandshakeRole
import com.fserver.net.security.IdentityStore
import com.fserver.net.security.NegotiatedParameters
import com.fserver.net.security.PeerAuthenticator
import com.fserver.net.security.PeerIdentity
import com.fserver.net.security.SecureChannel
import com.fserver.net.session.SessionLink
import com.fserver.net.spi.TransportCapabilities
import com.fserver.net.wire.Envelope
import com.fserver.net.wire.EnvelopeCodec
import com.fserver.net.wire.FrameKind
import com.fserver.net.wire.ProtocolVersions
import kotlin.time.Duration

/**
 * Runs the handshake over a freshly opened channel and hands back a sealed link.
 *
 * Unconditional, including on transports that encrypt themselves: Nearby protects the link but
 * proves nothing about *which* device is on it. [TransportCapabilities.isLinkEncrypted] only
 * affects which cipher suite is worth running, never whether this runs.
 *
 * Three frames: HELLO, HELLO_ACK, READY. The key-agreement public keys ride inside the first two -
 * a separate round trip would buy nothing, since neither side can act on the shared secret before
 * both hellos are in.
 */
internal class HandshakeNegotiator<M : Any>(
    private val identityStore: IdentityStore,
    private val dictionary: MessageDictionary<M>,
    private val crypto: CryptoProvider,
    /** Optional: absent means every peer that completes a handshake is trusted. */
    private val authenticator: PeerAuthenticator?,
    private val protocolVersions: IntRange,
    private val logger: NetLogger,
) {
    suspend fun negotiate(
        pump: FramePump,
        role: HandshakeRole,
        capabilities: TransportCapabilities,
        confirmationCode: String?,
        timeout: Duration,
    ): SessionLink = when (role) {
        HandshakeRole.Initiator -> initiate(pump, capabilities, confirmationCode, timeout)
        HandshakeRole.Responder -> respond(pump, capabilities, confirmationCode, timeout)
    }

    private suspend fun initiate(
        pump: FramePump,
        capabilities: TransportCapabilities,
        confirmationCode: String?,
        timeout: Duration,
    ): SessionLink {
        val keyExchange = crypto.newKeyExchange()

        write(pump, FrameKind.HELLO, hello(keyExchange.publicKey, capabilities, protocolVersions).encode())

        val ack = expect(pump, FrameKind.HELLO_ACK, timeout)
        val remote = HandshakeHello.decode(ack.payload)

        val version = remote.maxVersion
        if (version !in protocolVersions) {
            val reason = "protocol version $version is outside $protocolVersions"
            sendClose(pump, reason)
            throw HandshakeException(reason)
        }

        val peer = remote.toPeerIdentity()
        val dictionaryVersion = accept(pump, peer, remote, confirmationCode)

        write(pump, FrameKind.READY)

        return link(
            pump = pump,
            keyExchange = keyExchange,
            remote = remote,
            role = HandshakeRole.Initiator,
            capabilities = capabilities,
            protocolVersion = version,
            dictionaryVersion = dictionaryVersion,
            peer = peer,
        )
    }

    private suspend fun respond(
        pump: FramePump,
        capabilities: TransportCapabilities,
        confirmationCode: String?,
        timeout: Duration,
    ): SessionLink {
        val incoming = expect(pump, FrameKind.HELLO, timeout)
        val remote = HandshakeHello.decode(incoming.payload)

        val version = minOf(remote.maxVersion, protocolVersions.last)
        if (version < maxOf(remote.minVersion, protocolVersions.first)) {
            val reason = "no common protocol version: peer ${remote.minVersion}..${remote.maxVersion}, local $protocolVersions"
            sendClose(pump, reason)
            throw HandshakeException(reason)
        }

        val peer = remote.toPeerIdentity()
        val dictionaryVersion = accept(pump, peer, remote, confirmationCode)

        val keyExchange = crypto.newKeyExchange()
        write(pump, FrameKind.HELLO_ACK, hello(keyExchange.publicKey, capabilities, version..version).encode())

        expect(pump, FrameKind.READY, timeout)

        return link(
            pump = pump,
            keyExchange = keyExchange,
            remote = remote,
            role = HandshakeRole.Responder,
            capabilities = capabilities,
            protocolVersion = version,
            dictionaryVersion = dictionaryVersion,
            peer = peer,
        )
    }

    /** Trust gate, then the user's dictionary verdict. Both can end the handshake. */
    private suspend fun accept(
        pump: FramePump,
        peer: PeerIdentity,
        remote: HandshakeHello,
        confirmationCode: String?,
    ): Int {
        val verdict = authenticator?.verify(peer, confirmationCode) ?: AuthDecision.Trust
        if (verdict is AuthDecision.Reject) {
            sendClose(pump, "peer rejected: ${verdict.reason}")
            throw AuthenticationRejectedException(verdict.reason)
        }
        if (authenticator == null) {
            logger.warn("no PeerAuthenticator configured - trusting ${peer.deviceId} unconditionally")
        }

        return when (val decision = dictionary.negotiate(remote.dictionary)) {
            is DictionaryDecision.Accept -> decision.effectiveVersion
            is DictionaryDecision.Reject -> {
                sendClose(pump, "dictionary rejected: ${decision.reason}")
                throw DictionaryMismatchException(remote.dictionary, decision.reason)
            }
        }
    }

    private fun link(
        pump: FramePump,
        keyExchange: com.fserver.net.security.KeyExchange,
        remote: HandshakeHello,
        role: HandshakeRole,
        capabilities: TransportCapabilities,
        protocolVersion: Int,
        dictionaryVersion: Int,
        peer: PeerIdentity,
    ): SessionLink {
        val secret = keyExchange.sharedSecret(remote.keyExchangeKey)
        val secure = SecureChannel(
            inboundFrames = pump.remaining(),
            writer = pump::send,
            closer = pump::close,
            aead = crypto.aead(secret, role),
        )

        return SessionLink(
            secure = secure,
            negotiated = NegotiatedParameters(
                protocolVersion = protocolVersion,
                dictionaryVersion = dictionaryVersion,
                cipherSuite = crypto.suite,
                maxFrameSize = minOf(capabilities.maxFrameSize, remote.maxFrameSize),
                peer = peer,
            ),
        )
    }

    private fun hello(
        keyExchangeKey: ByteArray,
        capabilities: TransportCapabilities,
        versions: IntRange,
    ): HandshakeHello {
        val local = identityStore.local
        return HandshakeHello(
            minVersion = versions.first,
            maxVersion = versions.last,
            deviceId = local.deviceId,
            displayName = local.displayName,
            publicKey = local.publicKey,
            keyExchangeKey = keyExchangeKey,
            dictionary = dictionary.descriptor,
            maxFrameSize = capabilities.maxFrameSize,
            cipherSuite = crypto.suite.name,
        )
    }

    private suspend fun expect(pump: FramePump, kind: FrameKind, timeout: Duration): Envelope {
        val envelope = EnvelopeCodec.decode(pump.next(timeout))
        if (envelope.kind == FrameKind.CLOSE) {
            throw HandshakeException("peer closed the handshake: ${envelope.payload.decodeToString()}")
        }
        if (envelope.kind != kind) {
            throw HandshakeException("expected $kind, got ${envelope.kind}")
        }
        return envelope
    }

    private suspend fun write(pump: FramePump, kind: FrameKind, payload: ByteArray = Envelope.EMPTY) {
        pump.send(
            EnvelopeCodec.encode(
                Envelope(
                    version = ProtocolVersions.CURRENT,
                    kind = kind,
                    messageId = 0,
                    payload = payload,
                )
            )
        ).getOrThrow()
    }

    /** Tells the peer why before the caller fails - a silent drop looks like a broken network. */
    private suspend fun sendClose(pump: FramePump, reason: String) {
        runCatching { write(pump, FrameKind.CLOSE, reason.encodeToByteArray()) }
    }

    private fun HandshakeHello.toPeerIdentity() = PeerIdentity(
        deviceId = deviceId,
        displayName = displayName,
        publicKey = publicKey,
    )
}
