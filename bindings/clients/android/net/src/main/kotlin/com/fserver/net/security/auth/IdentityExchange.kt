package com.fserver.net.security.auth

import com.fserver.common.exception.NetworkException
import com.fserver.net.security.crypto.CryptoProvider
import com.fserver.net.security.identity.IdentityStore
import com.fserver.net.security.identity.PeerIdentity
import com.fserver.net.security.identity.PeerIdentityCodec
import com.fserver.net.wire.ByteReader
import com.fserver.net.wire.ByteWriter

/**
 * Who each side says it is, and the proof that it may say so. Run by the handshake for every
 * method, never by a method itself: it is the one step whose absence is invisible from the other
 * end, and a method that skipped it would hand the trust gate a key the peer merely claimed.
 *
 * The identity travels sealed under the key the method just agreed, and is signed over that
 * method's transcript, so a signature cannot be lifted out of one handshake into another.
 *
 * Public so that an [AuthMethod] implementer can see what becomes of an [AuthOutcome], not so that
 * a method can run it: one that did would put two identity exchanges on the wire.
 */
object IdentityExchange {

    suspend fun run(
        io: HandshakeIo,
        context: AuthContext,
        outcome: AuthOutcome,
        identityStore: IdentityStore,
    ): PeerIdentity {
        val send = suspend {
            val identity = PeerIdentityCodec.encode(context.local)
            val signature = identityStore.sign(popData(context.role, outcome.transcript, identity))
            io.send(outcome.aead.seal(SignedIdentity(identity, signature).encode()))
        }

        val receive = suspend {
            val received = SignedIdentity.decode(open(outcome.aead, io.receive()))

            PeerIdentityCodec.decode(received.identity).also { peer ->
                identityStore.verify(
                    publicKey = peer.publicKey,
                    data = popData(context.role.reverse(), outcome.transcript, received.identity),
                    signature = received.signature,
                )
            }
        }

        return when (context.role) {
            CryptoProvider.Role.Initiator -> {
                send()
                receive()
            }

            // The responder verifies first: answering a stranger with an identity it never earned
            // would turn the handshake into device enumeration on the LAN.
            CryptoProvider.Role.Responder -> receive().also { send() }
        }
    }

    /**
     * A frame that will not open means the two sides never agreed on a key - a wrong password, a
     * substituted peer - and that is a refusal, not a protocol fault.
     */
    private fun open(aead: CryptoProvider.Aead, payload: ByteArray): ByteArray = try {
        aead.open(payload)
    } catch (e: NetworkException.Protocol) {
        throw NetworkException.AuthenticationRejected("peer identity could not be opened", e)
    }

    /** Proof-of-possession data to sign: the label, who signed, the transcript, the identity. */
    private fun popData(
        signer: CryptoProvider.Role,
        transcript: ByteArray,
        identity: ByteArray,
    ): ByteArray = ByteWriter(POP_LABEL.size + transcript.size + identity.size + 16)
        .raw(POP_LABEL)
        .u8(if (signer == CryptoProvider.Role.Initiator) 0 else 1)
        .bytes(transcript)
        .bytes(identity)
        .toByteArray()

    private val POP_LABEL = "Proof-of-possession".encodeToByteArray()
}

private class SignedIdentity(
    val identity: ByteArray,
    val signature: ByteArray,
) {
    fun encode(): ByteArray = ByteWriter(4 + identity.size + 4 + signature.size)
        .bytes(identity)
        .bytes(signature)
        .toByteArray()

    companion object Codec {
        fun decode(bytes: ByteArray): SignedIdentity {
            val reader = ByteReader(bytes)
            val identity = reader.bytes()
            val signature = reader.bytes()
            if (reader.remaining != 0) {
                throw NetworkException.Protocol("signed identity has ${reader.remaining} trailing bytes")
            }
            return SignedIdentity(identity, signature)
        }
    }
}
