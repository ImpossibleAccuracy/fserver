package com.fserver.core.network.auth.impl

import com.fserver.core.network.auth.AuthMethod
import com.fserver.core.network.device.model.PendingConfirmation
import com.fserver.core.support.peerIdentity
import com.fserver.net.security.PeerAuthenticator
import com.fserver.net.security.auth.sas.SasAuthMethod
import com.fserver.net.security.trust.AuthStrength
import com.fserver.net.security.trust.TrustPrompt
import com.fserver.net.security.trust.TrustRecord
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The gate a peer cannot get through without a person.
 *
 * What the prompt carries is the whole basis for that person's answer, so the code the two screens
 * compare and the reason the prompt exists at all are asserted, not assumed.
 */
class InteractivePeerAuthenticatorTest {

    private val authenticator = InteractivePeerAuthenticator()

    @Test
    fun `accepting the prompt trusts the peer, and clears it`() = runTest {
        val decision = async { authenticator.verify(prompt()) }

        authenticator.pending.first { it != null }
        authenticator.resolve(accept = true)

        assertEquals(PeerAuthenticator.Decision.Trust, decision.await())
        assertNull(authenticator.pending.value)
    }

    @Test
    fun `refusing the prompt rejects the peer`() = runTest {
        val decision = async { authenticator.verify(prompt()) }

        authenticator.pending.first { it != null }
        authenticator.resolve(accept = false)

        assertTrue(decision.await() is PeerAuthenticator.Decision.Reject)
        assertNull(authenticator.pending.value)
    }

    @Test
    fun `the confirmation code reaches the screen in groups the user can read back`() =
        runTest {
            val decision = async { authenticator.verify(prompt(code = "1234 5678 90ab")) }

            val shown = authenticator.pending.first { it != null }!!
            assertEquals(listOf("1234", "5678", "90ab"), shown.codeGroups)

            authenticator.resolve(accept = true)
            decision.await()
        }

    @Test
    fun `a code too short to group is shown whole rather than split`() = runTest {
        val decision = async { authenticator.verify(prompt(code = "1234")) }

        val shown = authenticator.pending.first { it != null }!!
        assertEquals(listOf("1234"), shown.codeGroups)

        authenticator.resolve(accept = true)
        decision.await()
    }

    @Test
    fun `a method with no code at all shows none`() = runTest {
        val decision = async { authenticator.verify(prompt(code = null)) }

        val shown = authenticator.pending.first { it != null }!!
        assertEquals(emptyList<String>(), shown.codeGroups)
        assertTrue(shown.fingerprintGroups.isNotEmpty())

        authenticator.resolve(accept = true)
        decision.await()
    }

    @Test
    fun `a key that changed under a known device id says so, with what is on file`() = runTest {
        val pinned = trustRecord("previously pinned key")
        val decision = async {
            authenticator.verify(prompt(reason = TrustPrompt.Reason.KeyChanged(listOf(pinned))))
        }

        val shown = authenticator.pending.first { it != null }!!
        val reason = shown.reason as PendingConfirmation.Reason.KeyChanged

        // The user is being asked to tell a reinstall from an impersonation - without the
        // fingerprints already on file there is nothing to tell them apart by.
        assertEquals(listOf(pinned.fingerprint.value.split(" ")), reason.knownFingerprints)

        authenticator.resolve(accept = true)
        decision.await()
    }

    @Test
    fun `a peer that no longer has the pairing says which method made it`() = runTest {
        val decision = async {
            authenticator.verify(
                prompt(reason = TrustPrompt.Reason.PeerForgotUs(trustRecord("pinned")))
            )
        }

        val shown = authenticator.pending.first { it != null }!!
        assertEquals(
            PendingConfirmation.Reason.PeerForgotUs(AuthMethod.ConfirmFingerprint),
            shown.reason,
        )

        authenticator.resolve(accept = true)
        decision.await()
    }

    @Test
    fun `a downgrade says which method the key was pinned with`() = runTest {
        val decision = async {
            authenticator.verify(
                prompt(reason = TrustPrompt.Reason.Downgrade(trustRecord("pinned")))
            )
        }

        val shown = authenticator.pending.first { it != null }!!
        assertEquals(
            PendingConfirmation.Reason.Downgrade(AuthMethod.ConfirmFingerprint),
            shown.reason,
        )

        authenticator.resolve(accept = true)
        decision.await()
    }

    @Test
    fun `an answer with nothing waiting is dropped, not banked for the next peer`() = runTest {
        // A screen dismissed after its handshake gave up must not pre-accept whoever dials next.
        authenticator.resolve(accept = true)

        val decision = async { authenticator.verify(prompt()) }
        authenticator.pending.first { it != null }
        authenticator.resolve(accept = false)

        assertTrue(decision.await() is PeerAuthenticator.Decision.Reject)
    }

    private fun prompt(
        code: String? = "1234 5678",
        reason: TrustPrompt.Reason = TrustPrompt.Reason.FirstContact,
        peerKnowsUs: Boolean = false,
    ) = TrustPrompt(
        candidate = peerIdentity("device-peer"),
        method = SasAuthMethod.ID,
        strength = AuthStrength.UserCompared,
        confirmationCode = code,
        peerKnowsUs = peerKnowsUs,
        reason = reason,
    )

    private fun trustRecord(key: String) = TrustRecord(
        publicKey = key.toByteArray(),
        deviceId = "device-peer",
        displayName = "Peer",
        method = SasAuthMethod.ID,
        strength = AuthStrength.UserCompared,
        descriptor = null,
    )
}
