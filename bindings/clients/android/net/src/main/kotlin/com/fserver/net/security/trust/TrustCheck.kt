package com.fserver.net.security.trust

import com.fserver.common.exception.NetworkException
import com.fserver.net.security.identity.PeerIdentity

/**
 * The gate an **AuthMethod** calls once it has proven who is on the other end.
 */
fun interface TrustCheck {
    /**
     * @param peer what this method proved, not what the peer claimed.
     * @param confirmationCode the string the two ends compare, when this method derives one.
     * @throws NetworkException.AuthenticationRejected when the peer is not to be talked to.
     */
    suspend fun check(peer: PeerIdentity, confirmationCode: String?)
}
