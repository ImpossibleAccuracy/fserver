package com.fserver.net.security.auth

/**
 * Names one way of authenticating a peer, and with it the handshake pattern and the primitives
 * that way uses - `spake2-x25519-chacha20poly1305` rather than a `Password` constant. There is no
 * separate cipher-suite negotiation: the method decides everything.
 *
 * A string rather than an enum because the set is meant to grow (ToR §3.2, "extensible set of
 * authentication methods"), and because a peer may offer one this build has never heard of.
 */
@JvmInline
value class AuthMethodId(val value: String) {
    override fun toString(): String = value

    companion object {
        /** Nearby's own digit comparison. Only ever valid on transport that declares it. */
        val NEARBY_SAS: AuthMethodId = AuthMethodId("nearby-sas")
    }
}
