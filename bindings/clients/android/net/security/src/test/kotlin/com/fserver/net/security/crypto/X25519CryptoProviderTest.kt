package com.fserver.net.security.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class X25519CryptoProviderTest {

    @Test
    fun `both ends of a key exchange derive the same secret`() {
        val alice = X25519CryptoProvider.newKeyExchange()
        val bob = X25519CryptoProvider.newKeyExchange()

        assertArrayEquals(alice.sharedSecret(bob.publicKey), bob.sharedSecret(alice.publicKey))
    }

    @Test
    fun `each key exchange is ephemeral`() {
        val first = X25519CryptoProvider.newKeyExchange()
        val second = X25519CryptoProvider.newKeyExchange()

        assertFalse(first.publicKey.contentEquals(second.publicKey))
    }

    @Test
    fun `a frame sealed by one role opens on the other, in both directions`() {
        val secret = agreedSecret()
        val initiator = X25519CryptoProvider.aead(secret, CryptoProvider.Role.Initiator)
        val responder = X25519CryptoProvider.aead(secret, CryptoProvider.Role.Responder)

        val toResponder = initiator.seal(PLAINTEXT.encodeToByteArray())
        assertFalse(toResponder.toString(Charsets.ISO_8859_1).contains(PLAINTEXT))
        assertArrayEquals(PLAINTEXT.encodeToByteArray(), responder.open(toResponder))

        val toInitiator = responder.seal(PLAINTEXT.encodeToByteArray())
        assertArrayEquals(PLAINTEXT.encodeToByteArray(), initiator.open(toInitiator))
    }

    @Test
    fun `a tampered frame fails to open instead of decrypting to garbage`() {
        val secret = agreedSecret()
        val initiator = X25519CryptoProvider.aead(secret, CryptoProvider.Role.Initiator)
        val responder = X25519CryptoProvider.aead(secret, CryptoProvider.Role.Responder)

        val sealed = initiator.seal(PLAINTEXT.encodeToByteArray())
        sealed[0] = (sealed[0] + 1).toByte()

        assertThrows(Exception::class.java) { responder.open(sealed) }
    }

    @Test
    fun `frames out of the expected order fail to open`() {
        val secret = agreedSecret()
        val initiator = X25519CryptoProvider.aead(secret, CryptoProvider.Role.Initiator)
        val responder = X25519CryptoProvider.aead(secret, CryptoProvider.Role.Responder)

        val first = initiator.seal(PLAINTEXT.encodeToByteArray())
        val second = initiator.seal(PLAINTEXT.encodeToByteArray())

        // The receive counter expects nonce 0 first; `second` was sealed under nonce 1.
        assertThrows(Exception::class.java) { responder.open(second) }
    }

    private fun agreedSecret(): ByteArray {
        val alice = X25519CryptoProvider.newKeyExchange()
        val bob = X25519CryptoProvider.newKeyExchange()
        return alice.sharedSecret(bob.publicKey)
    }

    private companion object {
        const val PLAINTEXT = "top-secret-payload"
    }
}
