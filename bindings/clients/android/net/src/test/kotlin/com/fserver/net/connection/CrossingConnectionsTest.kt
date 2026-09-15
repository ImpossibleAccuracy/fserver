package com.fserver.net.connection.impl

import com.fserver.net.config.NetworkConfig
import com.fserver.net.config.NetworkConfigHolder
import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.connection.PeerRef
import com.fserver.net.connection.TimeoutsConfig
import com.fserver.net.security.identity.EphemeralIdentityStore
import com.fserver.net.session.PeerSession
import com.fserver.net.session.SessionLink
import com.fserver.net.support.LOOPBACK
import com.fserver.net.support.LoopbackEndpoint
import com.fserver.net.support.LoopbackNetwork
import com.fserver.net.support.TestDictionary
import com.fserver.net.support.TestMessage
import com.fserver.net.support.TestingAuthMethod
import com.fserver.net.support.handshake
import com.fserver.net.support.negotiator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Two devices dialling each other at once. Both links complete, and each end has one slot for the
 * device behind them, so one link has to be dropped - by the same rule on both ends, or the two
 * drop each other's and nobody is left connected.
 */
class CrossingConnectionsTest {

    private val scope = CoroutineScope(SupervisorJob())
    private val alice = EphemeralIdentityStore(deviceId = "device-a", displayName = "alice")
    private val bob = EphemeralIdentityStore(deviceId = "device-z", displayName = "bob")

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `crossing dials leave both ends on the link the lower device id opened`() = runBlocking {
        val aliceSide = holder(alice)
        val bobSide = holder(bob)

        // One link each way, as two simultaneous dials produce.
        val dialledByAlice = links(from = alice, to = bob)
        val dialledByBob = links(from = bob, to = alice)

        // The registration orders that disagree: each end takes in the link it accepted before the
        // one it dialled comes back. First-come-first-served would leave alice on her inbound link
        // and bob on his, each closing what the other kept.
        aliceSide.accept(dialledByBob.responder, from = "device-z")
        bobSide.accept(dialledByAlice.responder, from = "device-a")
        aliceSide.dial(dialledByAlice.initiator, to = "device-z")
        bobSide.dial(dialledByBob.initiator, to = "device-a")

        val aliceSession = aliceSide.sessions.value.getValue("device-z")
        val bobSession = bobSide.sessions.value.getValue("device-a")

        // Both on the link alice opened, because "device-a" < "device-z".
        assertTrue(aliceSession.dialled)
        assertFalse(bobSession.dialled)
        assertTrue(aliceSession.state.value is PeerSession.State.Ready)
        assertTrue(bobSession.state.value is PeerSession.State.Ready)
    }

    @Test
    fun `a second link in the same direction is the plain race, and loses it`() = runBlocking {
        val aliceSide = holder(alice)

        val first = links(from = alice, to = bob)
        val second = links(from = alice, to = bob)

        val kept = aliceSide.dial(first.initiator, to = "device-z")
        val spare = aliceSide.dial(second.initiator, to = "device-z")

        // Nothing to arbitrate: the session already in place is the one that stays.
        assertTrue(kept === spare)
        assertTrue(kept.state.value is PeerSession.State.Ready)
    }

    // ------------------------------------------------------------------ helpers

    private suspend fun ConnectionsHolder<TestMessage>.dial(link: SessionLink, to: String) =
        register(route = route(to), link = link, policy = POLICY, relink = { link })

    private suspend fun ConnectionsHolder<TestMessage>.accept(link: SessionLink, from: String) =
        register(route = route(from), link = link, policy = POLICY, relink = null)

    private fun route(deviceId: String) =
        PeerRef(deviceId, LOOPBACK, LoopbackEndpoint(deviceId))

    private fun holder(identity: EphemeralIdentityStore) = ConnectionsHolder(
        configHolder = NetworkConfigHolder(
            NetworkConfig(
                dictionary = TestDictionary(),
                identityStore = identity,
                authMethods = listOf(TestingAuthMethod()),
                transports = listOf(LoopbackNetwork().transport(identity.displayName)),
                policy = POLICY,
            )
        ),
        scope = scope,
    )

    /** One completed handshake, as both ends of it see it. */
    private suspend fun links(
        from: EphemeralIdentityStore,
        to: EphemeralIdentityStore,
    ): Links = scope.run {
        val (initiator, responder) = handshake(
            initiator = negotiator(from.displayName, identityStore = from, authMethods = emptyList()),
            responder = negotiator(to.displayName, identityStore = to, authMethods = emptyList()),
        )
        Links(initiator.getOrThrow(), responder.getOrThrow())
    }

    private class Links(val initiator: SessionLink, val responder: SessionLink)

    private companion object {
        // Deterministic: no keep-alive timers, and a dropped link stays dropped.
        val POLICY = ConnectionPolicy(timeouts = TimeoutsConfig(keepAlive = null), reconnect = null)
    }
}
