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

    inner class Session(private val method: AuthMethod) : TrustCheck {
        private var checked: PeerIdentity? = null
        private var pinned: TrustRecord? = null

        /** Whether this peer was already pinned before this handshake ran. */
        val wasKnown: Boolean get() = pinned != null

        override suspend fun check(peer: PeerIdentity, confirmationCode: String?) =
            gate(peer, confirmationCode) {
                "auth method ${method.id} asked about two different peers in one handshake"
            }

        private suspend fun gate(
            peer: PeerIdentity,
            confirmationCode: String?,
            onMismatch: (earlier: PeerIdentity) -> String,
        ) {
            checked?.let { earlier ->
                if (earlier != peer) {
                    throw NetworkException.Handshake(onMismatch(earlier))
                }
                return
            }

            val store = configHolder.current.trustStore
            val known = store?.find(peer.publicKey)
            pinned = known

            when {
                known == null -> {
                    // Same device ID, different key.
                    // Most often this is attack pattern, but it is easier ask user for solution.
                    val conflicting = store?.findByDeviceId(peer.deviceId)
                        ?.filterNot { it.publicKey.contentEquals(peer.publicKey) }
                        .orEmpty()

                    ask(
                        peer = peer,
                        confirmationCode = confirmationCode,
                        reason = when {
                            conflicting.isEmpty() -> TrustPrompt.Reason.FirstContact
                            else -> TrustPrompt.Reason.KeyChanged(conflicting)
                        },
                    )
                }

                method.strength < known.strength -> ask(
                    peer = peer,
                    confirmationCode = confirmationCode,
                    reason = TrustPrompt.Reason.Downgrade(known)
                )

                else -> configHolder.current.logger.debug(
                    "peer ${peer.fingerprint.value} is pinned for ${known.method}; not asking again"
                )
            }

            checked = peer
        }

        /**
         * The check the handshake runs itself once a method returns, so a method that never called
         * [check] cannot silently mean "trusted", and one that vouched for a different peer than
         * it returned cannot get past the seal.
         */
        suspend fun ensureChecked(peer: PeerIdentity) {
            if (checked == null) {
                configHolder.current.logger.warn(
                    "auth method ${method.id} returned without a trust check - running one with no confirmation string"
                )
            }
            gate(peer, null) { earlier ->
                "auth method ${method.id} vouched for ${earlier.fingerprint.value} " +
                        "but returned ${peer.fingerprint.value}"
            }
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
            reason: TrustPrompt.Reason,
        ) {
            val authenticator = configHolder.current.authenticator ?: return
            val verdict = authenticator.verify(
                TrustPrompt(
                    candidate = peer,
                    method = method.id,
                    strength = method.strength,
                    confirmationCode = confirmationCode,
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
