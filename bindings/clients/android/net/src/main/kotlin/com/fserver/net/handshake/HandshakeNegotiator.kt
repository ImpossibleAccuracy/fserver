package com.fserver.net.handshake

import com.fserver.net.NetLogger
import com.fserver.net.NetworkException
import com.fserver.net.dictionary.MessageDictionary
import com.fserver.net.peer.PeerDescriptor
import com.fserver.net.peer.PeerDescriptorCodec
import com.fserver.net.security.CryptoProvider
import com.fserver.net.security.IdentityStore
import com.fserver.net.security.NegotiatedParameters
import com.fserver.net.security.PeerAuthenticator
import com.fserver.net.security.PeerIdentity
import com.fserver.net.security.SecureChannel
import com.fserver.net.session.SessionLink
import com.fserver.net.spi.TransportCapabilities
import com.fserver.net.wire.Envelope
import com.fserver.net.wire.FrameKind
import com.fserver.net.wire.ProtocolVersions
import kotlin.time.Duration

/**
 * Runs the handshake over a freshly opened channel and hands back a sealed link.
 *
 * Unconditional, including on transports that encrypt themselves: Nearby protects the link but
 * proves nothing about *which* device is on it. [com.fserver.net.spi.TransportCapabilities.isLinkEncrypted] only
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
        role: CryptoProvider.Role,
        capabilities: TransportCapabilities,
        confirmationCode: String?,
        timeout: Duration,
    ): SessionLink = when (role) {
        CryptoProvider.Role.Initiator -> initiate(pump, capabilities, confirmationCode, timeout)
        CryptoProvider.Role.Responder -> respond(pump, capabilities, confirmationCode, timeout)
    }

    private suspend fun initiate(
        pump: FramePump,
        capabilities: TransportCapabilities,
        confirmationCode: String?,
        timeout: Duration,
    ): SessionLink {
        val keyExchange = crypto.newKeyExchange()

        write(
            pump = pump,
            kind = FrameKind.HELLO,
            payload = hello(
                keyExchangeKey = keyExchange.publicKey,
                capabilities = capabilities,
                versions = protocolVersions
            ).encode()
        )

        val ack = expect(pump, FrameKind.HELLO_ACK, timeout)
        val remote = HandshakeHello.decode(ack.payload)

        val version = remote.maxVersion
        if (version !in protocolVersions) {
            val reason = "protocol version $version is outside $protocolVersions"
            sendClose(pump = pump, reason = reason)
            throw NetworkException.Handshake(reason)
        }

        val peer = remote.toPeerIdentity()
        val dictionaryVersion = accept(
            pump = pump,
            peer = peer,
            remote = remote,
            confirmationCode = confirmationCode,
        )

        write(
            pump = pump,
            kind = FrameKind.DESCRIPTOR_REQUEST,
            payload = PeerDescriptorCodec.encode(localDescriptor())
        )
        val descriptorResponse = expect(
            pump = pump,
            kind = FrameKind.DESCRIPTOR_RESPONSE,
            timeout = timeout
        )
        val peerDescriptor = PeerDescriptorCodec.decode(
            descriptorResponse.payload
        )

        write(pump = pump, kind = FrameKind.READY)

        return link(
            pump = pump,
            keyExchange = keyExchange,
            remote = remote,
            role = CryptoProvider.Role.Initiator,
            capabilities = capabilities,
            protocolVersion = version,
            dictionaryVersion = dictionaryVersion,
            peer = peer,
            peerDescriptor = peerDescriptor,
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
            val reason =
                "no common protocol version: peer ${remote.minVersion}..${remote.maxVersion}, local $protocolVersions"
            sendClose(pump, reason)
            throw NetworkException.Handshake(reason)
        }

        val peer = remote.toPeerIdentity()
        val dictionaryVersion = accept(
            pump = pump,
            peer = peer,
            remote = remote,
            confirmationCode = confirmationCode,
        )

        val keyExchange = crypto.newKeyExchange()
        write(
            pump,
            FrameKind.HELLO_ACK,
            hello(keyExchange.publicKey, capabilities, version..version).encode()
        )

        val incomingDescriptor = expect(
            pump = pump,
            kind = FrameKind.DESCRIPTOR_REQUEST,
            timeout = timeout
        )
        val peerDescriptor = PeerDescriptorCodec.decode(
            incomingDescriptor.payload
        )
        write(
            pump = pump,
            kind = FrameKind.DESCRIPTOR_RESPONSE,
            payload = PeerDescriptorCodec.encode(localDescriptor())
        )

        expect(pump, FrameKind.READY, timeout)

        return link(
            pump = pump,
            keyExchange = keyExchange,
            remote = remote,
            role = CryptoProvider.Role.Responder,
            capabilities = capabilities,
            protocolVersion = version,
            dictionaryVersion = dictionaryVersion,
            peer = peer,
            peerDescriptor = peerDescriptor,
        )
    }

    /** Trust gate, then the user's dictionary verdict. Both can end the handshake. */
    private suspend fun accept(
        pump: FramePump,
        peer: PeerIdentity,
        remote: HandshakeHello,
        confirmationCode: String?,
    ): Int {
        val verdict =
            authenticator?.verify(peer, confirmationCode) ?: PeerAuthenticator.Decision.Trust
        if (verdict is PeerAuthenticator.Decision.Reject) {
            sendClose(pump, "peer rejected: ${verdict.reason}")
            throw NetworkException.AuthenticationRejected(verdict.reason)
        }
        if (authenticator == null) {
            logger.warn("no PeerAuthenticator configured - trusting ${peer.deviceId} unconditionally")
        }

        return when (val decision = dictionary.negotiate(remote.dictionary)) {
            is MessageDictionary.Decision.Accept -> decision.effectiveVersion
            is MessageDictionary.Decision.Reject -> {
                sendClose(pump, "dictionary rejected: ${decision.reason}")
                throw NetworkException.DictionaryMismatch(remote.dictionary, decision.reason)
            }
        }
    }

    /** Open secured channel to peer. */
    private fun link(
        pump: FramePump,
        keyExchange: CryptoProvider.KeyExchange,
        remote: HandshakeHello,
        role: CryptoProvider.Role,
        capabilities: TransportCapabilities,
        protocolVersion: Int,
        dictionaryVersion: Int,
        peer: PeerIdentity,
        peerDescriptor: PeerDescriptor,
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
                peerDescriptor = peerDescriptor,
            ),
        )
    }

    /** What this side sends over `DESCRIPTOR_REQUEST`/`DESCRIPTOR_RESPONSE`. */
    private fun localDescriptor(): PeerDescriptor {
        val local = identityStore.local
        return PeerDescriptor(
            deviceId = local.deviceId,
            displayName = local.displayName,
            kind = local.kind,
            accessMode = local.accessMode,
            advertised = PeerDescriptor.Advertised(
                protocolVersions = protocolVersions,
                fingerprint = local.fingerprint,
                dictionaryId = dictionary.descriptor.id,
                dictionaryVersion = dictionary.descriptor.version,
            ),
        )
    }

    /** Build a hello frame for the peer to decode. */
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

    /** Wait for a frame of [kind] from [pump], or throw if the peer closed or sent the wrong kind. */
    private suspend fun expect(pump: FramePump, kind: FrameKind, timeout: Duration): Envelope {
        val envelope = Envelope.Codec.decode(pump.next(timeout))
        if (envelope.kind == FrameKind.CLOSE) {
            throw NetworkException.Handshake("peer closed the handshake: ${envelope.payload.decodeToString()}")
        }
        if (envelope.kind != kind) {
            throw NetworkException.Handshake("expected $kind, got ${envelope.kind}")
        }
        return envelope
    }

    /** Send [payload] to [pump] */
    private suspend fun write(
        pump: FramePump,
        kind: FrameKind,
        payload: ByteArray = Envelope.EMPTY,
    ) {
        pump.send(
            Envelope.Codec.encode(
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
}

private fun HandshakeHello.toPeerIdentity() = PeerIdentity(
    deviceId = deviceId,
    displayName = displayName,
    publicKey = publicKey,
)
