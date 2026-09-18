package com.fserver.net.security.auth

import com.fserver.common.exception.NetworkException
import com.fserver.net.security.crypto.CryptoProvider

/**
 * One byte each way, sealed: "the key you just proved is already pinned here".
 *
 * Run by the handshake for every method, right after [IdentityExchange] - before that neither end
 * knows which key the other holds, so neither can answer. Sealed under the method's handshake key
 * like the identity it follows, so the answer comes from the peer that was just proven and not
 * from whoever is on the wire.
 *
 * It is a claim, never a proof: a peer is free to lie in either direction, and claiming to know us
 * buys an attacker exactly what a peer that really knew us already had. So nothing is *granted* on
 * the strength of it. What it is for is the opposite case - we kept the pairing and the peer did
 * not - which a pin would otherwise wave through silently. That is a reinstall, a restored backup,
 * or somebody else holding a copy of a key, and all three are the user's call (ToR §6.4).
 *
 * Public for the same reason [IdentityExchange] is: a method implementer should be able to see
 * what happens around an [AuthOutcome], not run it. A method that ran this would put two of these
 * exchanges on the wire.
 */
object KnownPeerExchange {

    suspend fun run(io: HandshakeIo, aead: CryptoProvider.Aead, knowsPeer: Boolean): Boolean {
        val answer = byteArrayOf(if (knowsPeer) KNOWN else UNKNOWN)
        val reply = open(aead, io.exchange(aead.seal(answer)))

        if (reply.size != 1) {
            throw NetworkException.Protocol("trust hint is ${reply.size} bytes, expected 1")
        }
        return when (reply.single()) {
            KNOWN -> true
            UNKNOWN -> false
            else -> throw NetworkException.Protocol("trust hint is ${reply.single()}, expected 0 or 1")
        }
    }

    /** As in [IdentityExchange]: a frame that will not open means the keys never agreed. */
    private fun open(aead: CryptoProvider.Aead, payload: ByteArray): ByteArray = try {
        aead.open(payload)
    } catch (e: NetworkException.Protocol) {
        throw NetworkException.AuthenticationRejected("peer trust hint could not be opened", e)
    }

    private const val UNKNOWN: Byte = 0
    private const val KNOWN: Byte = 1
}
