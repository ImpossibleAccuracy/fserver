package com.fserver.core.network.device.model

import com.fserver.core.network.auth.AuthMethod

/**
 * A peer mid-handshake, waiting on this device's answer to an out-of-band code comparison.
 *
 * [reason] is what the answer actually turns on. A code comparison needs the other device in front
 * of the user, and the case worth catching - a key that changed under a known device id - is the
 * one where that device is not there to compare against. A prompt that does not say why it is
 * being shown is a prompt the user cannot answer.
 */
data class PendingConfirmation(
    val deviceId: String,
    val codeGroups: List<String>,
    /** Fingerprint of the key being offered, already grouped for display. */
    val fingerprintGroups: List<String>,
    val reason: Reason,
) {
    sealed interface Reason {
        /** Never seen this key, and nothing on file contradicts it. */
        data object FirstContact : Reason

        /** Known key, authenticated with a weaker method than the one it was pinned with. */
        data class Downgrade(val pinnedMethod: AuthMethod?) : Reason

        /**
         * A key never seen before, claiming a device id that is on file under other keys.
         * A reinstalled peer looks like this - and so does someone announcing that device's id.
         */
        data class KeyChanged(val knownFingerprints: List<List<String>>) : Reason

        /**
         * This device still has the pairing and the peer says it does not. A reinstalled or
         * restored peer looks like this - and so does someone else holding a copy of its key.
         */
        data class PeerForgotUs(val pinnedMethod: AuthMethod?) : Reason
    }
}
