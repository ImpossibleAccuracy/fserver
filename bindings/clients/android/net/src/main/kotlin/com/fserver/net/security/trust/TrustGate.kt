package com.fserver.net.security.trust

import com.fserver.common.exception.NetworkException
import com.fserver.net.config.NetworkConfigHolder
import com.fserver.net.peer.PeerDescriptor
import com.fserver.net.security.PeerAuthenticator
import com.fserver.net.security.auth.AuthMethod
import com.fserver.net.security.identity.PeerIdentity
import kotlinx.coroutines.CancellationException

/**
 * Decides whether the user has to be asked about a peer, and remembers the answer as a pin.
 *
 * The policy lives here rather than in each [AuthMethod] for the reason §6.3 gives: what is
 * cacheable is the proven key, never the verdict. A method that reached its own conclusion and a
 * host that stored its own "yes" are the two ways that rule gets broken, so a method only reports
 * what it proved and the host only answers questions this class decided were worth asking.
 */
internal class TrustGate(
    private val configHolder: NetworkConfigHolder<*>,
) {
    /** One connection's worth of state. Not reusable - the pin it commits is that handshake's. */
    fun open(method: AuthMethod): Session = Session(method)

    inner class Session(private val method: AuthMethod) {
        private var checked: PeerIdentity? = null
        private var pinned: TrustRecord? = null
        private var looked = false

        /** Whether this peer was already pinned before this handshake ran. */
        val wasKnown: Boolean get() = pinned != null

        /** Whether [peer] is already pinned here */
        suspend fun knows(peer: PeerIdentity): Boolean = lookup(peer) != null

        /**
         * Run by the handshake once the peer's key is proven, and by nothing else: what is asked
         * about is what the identity exchange verified, never what a method chose to report.
         *
         * @param peer what the handshake proved, not what the peer claimed.
         * @param confirmationCode the string the two ends compare, when the method derived one.
         * @param peerKnowsUs what the peer said about its own side of the pairing. A claim, not a
         * proof, and only ever a reason to ask more - never a reason to ask less.
         * @param keyVerifiedOutOfBand true when the user vouched for this peer off the link, for this
         * connection - handed the method its key, or gave both ends the secret it proved.
         *
         * @throws NetworkException.AuthenticationRejected when the peer is not to be talked to.
         */
        suspend fun check(
            peer: PeerIdentity,
            confirmationCode: String?,
            peerKnowsUs: Boolean,
            keyVerifiedOutOfBand: Boolean,
        ) {
            checked?.let { earlier ->
                if (earlier != peer) {
                    throw NetworkException.Handshake(
                        "the gate was asked about two different peers in one handshake"
                    )
                }
                return
            }

            val store = configHolder.current.trustStore
            val known = lookup(peer)

            when {
                known == null -> {
                    // Same device ID, different key.
                    // Most often this is attack pattern, but it is easier ask user for solution.
                    val conflicting = store?.findByDeviceId(peer.deviceId)
                        ?.filterNot { it.publicKey.contentEquals(peer.publicKey) }
                        .orEmpty()

                    when {
                        conflicting.isNotEmpty() -> ask(
                            peer = peer,
                            confirmationCode = confirmationCode,
                            peerKnowsUs = peerKnowsUs,
                            reason = TrustPrompt.Reason.KeyChanged(conflicting),
                        )

                        // Asking now would be asking the user to confirm what they themselves just
                        // carried over - the prompt exists for peers that arrived over the link
                        // alone, and this one did not.
                        keyVerifiedOutOfBand -> configHolder.current.logger.debug(
                            "peer ${peer.fingerprint.value} was vouched for out of band; not asking"
                        )

                        else -> ask(
                            peer = peer,
                            confirmationCode = confirmationCode,
                            peerKnowsUs = peerKnowsUs,
                            reason = TrustPrompt.Reason.FirstContact,
                        )
                    }
                }

                method.strength < known.strength -> ask(
                    peer = peer,
                    confirmationCode = confirmationCode,
                    peerKnowsUs = peerKnowsUs,
                    reason = TrustPrompt.Reason.Downgrade(known)
                )

                // A pin records a pairing both ends made. One that only this end still has is the
                // case a pin must not silence: whatever produced it - a reinstall, a restored
                // backup, a copied key - the user is the one who can tell which.
                !peerKnowsUs -> ask(
                    peer = peer,
                    confirmationCode = confirmationCode,
                    peerKnowsUs = false,
                    reason = TrustPrompt.Reason.PeerForgotUs(known),
                )

                else -> configHolder.current.logger.debug(
                    "peer ${peer.fingerprint.value} is pinned for ${known.method}; not asking again"
                )
            }

            checked = peer
        }

        /** The store lookup this handshake runs once, whoever asks for it first. */
        private suspend fun lookup(peer: PeerIdentity): TrustRecord? {
            if (!looked) {
                pinned = configHolder.current.trustStore?.find(peer.publicKey)
                looked = true
            }
            return pinned
        }

        /**
         * Pins what this handshake proved, under the name that arrived sealed. Run after the
         * descriptor: before it there is no name worth storing, and §6.4 is about a name and a key
         * together.
         *
         * A pin failure costs a prompt next time and nothing else, so it never takes down a
         * session that otherwise came up.
         */
        suspend fun commit(descriptor: PeerDescriptor) {
            val peer = checked ?: return
            val config = configHolder.current
            val store = config.trustStore ?: return

            val previous = pinned
            val best = previous?.takeIf { it.strength > method.strength }

            try {
                store.pin(
                    TrustRecord(
                        publicKey = peer.publicKey,
                        deviceId = peer.deviceId,
                        displayName = descriptor.displayName,
                        method = best?.method ?: method.id,
                        strength = best?.strength ?: method.strength,
                        descriptor = descriptor,
                    )
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                config.logger.warn(
                    "could not pin ${peer.fingerprint.value}; it will be asked about again",
                    e
                )
            }
        }

        /** Ask authenticator to verify the peer, throwing if it rejects. */
        private suspend fun ask(
            peer: PeerIdentity,
            confirmationCode: String?,
            peerKnowsUs: Boolean,
            reason: TrustPrompt.Reason,
        ) {
            val authenticator = configHolder.current.authenticator ?: return
            val verdict = authenticator.verify(
                TrustPrompt(
                    candidate = peer,
                    method = method.id,
                    strength = method.strength,
                    confirmationCode = confirmationCode,
                    peerKnowsUs = peerKnowsUs,
                    reason = reason,
                )
            )

            when (verdict) {
                PeerAuthenticator.Decision.Trust -> {
                    // nothing to do
                }

                is PeerAuthenticator.Decision.Reject ->
                    throw NetworkException.AuthenticationRejected(verdict.reason)
            }
        }
    }
}
