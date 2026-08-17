package com.fserver.net.security.trust

import com.fserver.net.NetworkException
import com.fserver.net.security.PeerAuthenticator
import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.security.auth.AuthRequest
import com.fserver.net.security.identity.EphemeralIdentityStore
import com.fserver.net.session.SessionLink
import com.fserver.net.support.InMemoryTrustStore
import com.fserver.net.support.TestingAuthMethod
import com.fserver.net.support.handshake
import com.fserver.net.support.negotiator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What a device is allowed to remember about a peer, and what that saves the user next time.
 *
 * Two handshakes between the same pair of negotiators are two connections between the same two
 * devices: the identity stores, and so the keys, outlive them.
 */
class TrustGateTest {

    private val scope = CoroutineScope(SupervisorJob())

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `a peer pinned once is not put in front of the user again`() = runBlocking {
        val prompts = mutableListOf<TrustPrompt>()
        val store = InMemoryTrustStore()
        val alice = negotiator("alice")
        val bob = negotiator("bob", authenticator = record(prompts), trustStore = store)

        val first = scope.handshake(alice, bob).responder()
        val second = scope.handshake(alice, bob).responder()

        assertEquals(1, prompts.size)
        assertTrue(prompts.single().reason is TrustPrompt.Reason.FirstContact)
        assertFalse(first.negotiated.peerWasKnown)
        assertTrue(second.negotiated.peerWasKnown)
    }

    @Test
    fun `what is pinned is the proven key under the name that arrived sealed`() = runBlocking {
        val store = InMemoryTrustStore()
        val aliceIdentity = EphemeralIdentityStore(displayName = "alice's phone")

        scope.handshake(
            initiator = negotiator("alice", identityStore = aliceIdentity),
            responder = negotiator("bob", authenticator = trustAll, trustStore = store),
        ).responder()

        val pinned = store.pinned.single()
        assertEquals(aliceIdentity.local().deviceId, pinned.deviceId)
        assertTrue(pinned.publicKey.contentEquals(aliceIdentity.local().publicKey))
        assertEquals("alice's phone", pinned.displayName)
        assertEquals(TestingAuthMethod.ID, pinned.method)
        assertEquals(AuthStrength.UserCompared, pinned.strength)
    }

    @Test
    fun `the same device id under a new key is shown as a change, never waved through`() =
        runBlocking {
            val prompts = mutableListOf<TrustPrompt>()
            val store = InMemoryTrustStore()
            val bob = negotiator("bob", authenticator = record(prompts), trustStore = store)
            val deviceId = "alice-device"

            // Same claimed id, different key pair - a reinstall, or someone helping themselves to
            // the name.
            scope.handshake(
                initiator = negotiator(
                    "alice",
                    identityStore = EphemeralIdentityStore(deviceId, "alice"),
                ),
                responder = bob,
            ).responder()
            scope.handshake(
                initiator = negotiator(
                    "alice",
                    identityStore = EphemeralIdentityStore(deviceId, "alice"),
                ),
                responder = bob,
            ).responder()

            assertEquals(2, prompts.size)
            val second = prompts[1].reason
            assertTrue(second is TrustPrompt.Reason.KeyChanged)
            assertEquals(
                deviceId,
                (second as TrustPrompt.Reason.KeyChanged).pinned.single().deviceId
            )
        }

    @Test
    fun `a weaker method than the pinned one asks again, and does not lower the pin`() =
        runBlocking {
            val prompts = mutableListOf<TrustPrompt>()
            val store = InMemoryTrustStore()
            val alice = negotiator("alice", authMethods = listOf(weak()))
            val bob = negotiator(
                "bob",
                authMethods = listOf(weak()),
                authenticator = record(prompts),
                trustStore = store,
            )

            scope.handshake(alice, bob, request = AuthRequest(TestingAuthMethod.ID)).responder()
            scope.handshake(alice, bob, request = AuthRequest(WEAK)).responder()
            // Accepting one downgrade must not become the new bar, so the third asks too.
            scope.handshake(alice, bob, request = AuthRequest(WEAK)).responder()

            assertEquals(3, prompts.size)
            for (prompt in prompts.drop(1)) {
                val downgrade = prompt.reason
                assertTrue(downgrade is TrustPrompt.Reason.Downgrade)
                assertEquals(
                    TestingAuthMethod.ID,
                    (downgrade as TrustPrompt.Reason.Downgrade).pinned.method,
                )
            }

            val result = store.pinned.single()
            assertEquals(TestingAuthMethod.ID, result.method)
            assertEquals(AuthStrength.UserCompared, result.strength)
        }

    @Test
    fun `a refused peer is not pinned`() = runBlocking {
        val store = InMemoryTrustStore()
        val (initiator, responder) = scope.handshake(
            responder = negotiator(
                "bob",
                authenticator = { PeerAuthenticator.Decision.Reject("not this one") },
                trustStore = store,
            ),
        )

        assertTrue(responder.exceptionOrNull() is NetworkException.AuthenticationRejected)
        assertTrue(initiator.isFailure)
        assertTrue(store.pinned.isEmpty())
    }

    @Test
    fun `a method that never asks is gated by the handshake anyway`() = runBlocking {
        // §7.2 trusts SPIs, but a method that returns a peer without going through the gate would
        // otherwise mean "trusted" - so the handshake runs the check itself before it seals.
        val prompts = mutableListOf<TrustPrompt>()

        scope.handshake(
            initiator = negotiator(
                "alice",
                authMethods = listOf(TestingAuthMethod(skipTrust = true))
            ),
            responder = negotiator(
                "bob",
                authMethods = listOf(TestingAuthMethod(skipTrust = true)),
                authenticator = record(prompts),
                trustStore = InMemoryTrustStore(),
            ),
        ).responder()

        assertEquals(1, prompts.size)
        assertEquals(null, prompts.single().confirmationCode)
    }

    @Test
    fun `a store that cannot write costs a prompt next time, not the session`() = runBlocking {
        val prompts = mutableListOf<TrustPrompt>()
        val alice = negotiator("alice")
        val bob = negotiator(
            "bob",
            authenticator = record(prompts),
            trustStore = InMemoryTrustStore(failOnPin = true),
        )

        scope.handshake(alice, bob).responder()
        scope.handshake(alice, bob).responder()

        assertEquals(2, prompts.size)
    }

    private fun record(into: MutableList<TrustPrompt>) = PeerAuthenticator { prompt ->
        into += prompt
        PeerAuthenticator.Decision.Trust
    }

    private val trustAll = PeerAuthenticator { PeerAuthenticator.Decision.Trust }

    /** Same protocol as the default test method, declared as something worth less. */
    private fun weak() = TestingAuthMethod(strength = AuthStrength.SharedSecret, id = WEAK)

    /** The responder's link, or the failure that stopped it. */
    private fun Pair<Result<SessionLink>, Result<SessionLink>>.responder(): SessionLink =
        second.getOrThrow()

    private companion object {
        val WEAK = AuthMethodId("weak-testing")
    }
}
