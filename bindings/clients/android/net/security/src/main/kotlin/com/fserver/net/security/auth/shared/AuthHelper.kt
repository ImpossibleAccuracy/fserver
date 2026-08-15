package com.fserver.net.security.auth.shared

import com.fserver.net.NetworkException
import com.fserver.net.security.auth.AuthContext
import com.fserver.net.security.auth.HandshakeIo
import com.fserver.net.security.crypto.CryptoProvider
import com.fserver.net.security.crypto.IdentitySignature
import com.fserver.net.security.identity.PeerIdentity
import com.fserver.net.security.identity.PeerIdentityCodec
import com.fserver.net.wire.ByteReader
import com.fserver.net.wire.ByteWriter
import dev.whyoleg.cryptography.BinarySize.Companion.bytes
import dev.whyoleg.cryptography.CryptographyProvider
import dev.whyoleg.cryptography.algorithms.HKDF
import dev.whyoleg.cryptography.algorithms.SHA256

private const val DERIVED_KEY_SIZE = 32
private val POP_LABEL = "Proof-of-possession".encodeToByteArray()

object AuthHelper {
    val cryptographyProvider = CryptographyProvider.Default

    fun bind(
        secret: ByteArray,
        prologue: ByteArray,
        label: ByteArray
    ): ByteArray = cryptographyProvider.get(SHA256).hasher()
        .createHashFunction().use {
            it.update(label)
            it.update(secret)
            it.update(prologue)

            it.hashToByteArray()
        }

    suspend fun deriveKey(
        secret: ByteArray,
        info: ByteArray,
        keySize: Int = DERIVED_KEY_SIZE
    ): ByteArray = cryptographyProvider.get(HKDF)
        .secretDerivation(
            digest = SHA256,
            outputSize = keySize.bytes,
            salt = null,
            info = info,
        )
        .deriveSecretToByteArray(secret)

    /**
     * Send our identity using private key to sign the transcript and prove we are the owner of the identity.
     * The peer will verify the signature using the public key in the identity.
     */
    suspend fun receivePeerIdentity(
        context: AuthContext,
        transcript: ByteArray,
        io: HandshakeIo,
        aead: CryptoProvider.Aead,
    ): PeerIdentity {
        val sendIdentity = suspend {
            val identityBytes = PeerIdentityCodec.encode(context.local)
            val signature = context.sign(popData(context.role, transcript, identityBytes))
            io.send(
                aead.seal(
                    EncryptedIdentity(identityBytes, signature).encode()
                )
            )
        }

        val receiveIdentity = suspend {
            val encryptedPeerIdentity = EncryptedIdentity.decode(aead.open(io.receive()))
            PeerIdentityCodec.decode(encryptedPeerIdentity.identity)
                .also { peer ->
                    val peerRole = context.role.reverse()

                    // Verify before returning
                    IdentitySignature.verify(
                        publicKey = peer.publicKey,
                        data = popData(peerRole, transcript, encryptedPeerIdentity.identity),
                        signature = encryptedPeerIdentity.signature
                    )
                }
        }

        return when (context.role) {
            CryptoProvider.Role.Initiator -> {
                // If we are initiator, send our identity first.
                sendIdentity()
                receiveIdentity()
            }

            CryptoProvider.Role.Responder -> {
                // If we are responder, verify peer's identity first.
                // It secures from active device enumeration on LAN
                receiveIdentity().also {
                    sendIdentity()
                }
            }
        }
    }

    /** Proof-of-possession data to sign, including the transcript and identity. */
    private fun popData(
        signer: CryptoProvider.Role,
        transcript: ByteArray,
        identityBytes: ByteArray,
    ): ByteArray = ByteWriter(POP_LABEL.size + transcript.size + identityBytes.size + 16)
        .raw(POP_LABEL)
        .u8(if (signer == CryptoProvider.Role.Initiator) 0 else 1)
        .bytes(transcript)
        .bytes(identityBytes)
        .toByteArray()
}

private class EncryptedIdentity(
    val identity: ByteArray,
    val signature: ByteArray
) {
    fun encode(): ByteArray =
        ByteWriter(4 + identity.size + 4 + signature.size)
            .bytes(identity)
            .bytes(signature)
            .toByteArray()

    companion object Codec {
        fun decode(bytes: ByteArray): EncryptedIdentity {
            val reader = ByteReader(bytes)
            val identity = reader.bytes()
            val signature = reader.bytes()
            if (reader.remaining != 0) {
                throw NetworkException.Protocol("Encrypted identity has ${reader.remaining} trailing bytes")
            }
            return EncryptedIdentity(identity, signature)
        }
    }
}
