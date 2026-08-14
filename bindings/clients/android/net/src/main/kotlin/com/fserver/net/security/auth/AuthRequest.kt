package com.fserver.net.security.auth

/**
 * What the caller settled before dialling - typically after showing the user a prompt built from a
 * [com.fserver.net.peer.PublicGreeting].
 *
 * [method] is pinning, not preference. The greeting that produced the prompt came over a different
 * connection and is not trusted; naming the method here means a peer that no longer offers it ends
 * the attempt rather than quietly running something weaker. Leave it null to take whatever the two
 * sides have in common.
 *
 * A real PAKE will add the secret here.
 */
data class AuthRequest(val method: AuthMethodId? = null)
