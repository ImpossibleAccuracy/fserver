package com.fserver.net.security.trust

import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.security.identity.PeerIdentity

/**
 * The question put to the user, and why it is being asked at all.
 * Peer already pinned at this strength never produces one - skipping it is what a pin buys.
 */
class TrustPrompt(
    /** Proven by the method that is asking, not claimed by the peer. */
    val candidate: PeerIdentity,
    val method: AuthMethodId,
    val strength: AuthStrength,
    /** The string the two ends are meant to compare, when the method derived one. */
    val confirmationCode: String?,
    val reason: Reason,
) {
    sealed interface Reason {
        /** Never seen this key, and nothing on file contradicts it. */
        data object FirstContact : Reason

        /**
         * Known key, weaker method than the one it was pinned with.
         * Never silent, so decision is the user's and the prompt has to say what is being given up.
         */
        data class Downgrade(val pinned: TrustRecord) : Reason

        /**
         * A key never seen before, claiming a device id that is on file under other keys.
         * Accepting is possible (a peer may have been reinstalled) but it cannot be the quiet path.
         */
        data class KeyChanged(val pinned: List<TrustRecord>) : Reason
    }
}
