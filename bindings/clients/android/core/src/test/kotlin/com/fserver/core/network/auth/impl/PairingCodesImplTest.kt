package com.fserver.core.network.auth.impl

import com.fserver.core.network.auth.PairingCodeState
import com.fserver.core.support.MutableTimeProvider
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

class PairingCodesImplTest {
    private val clock = MutableTimeProvider()

    private fun TestScope.codes() = PairingCodesImpl(clock, backgroundScope, ttl = 1.minutes)

    @Test
    fun `issued code is six digits and taken once`() = runTest {
        val codes = codes()
        codes.issue()
        val active = codes.state.value as PairingCodeState.Active

        assertTrue(active.code.matches(Regex("\\d{6}")))
        assertEquals(active.code, codes.take())
        assertNull(codes.take())
        assertEquals(PairingCodeState.Spent, codes.state.value)
    }

    @Test
    fun `confirmed attempt marks the code used`() = runTest {
        val codes = codes()
        codes.issue()
        codes.take()
        codes.onUsed()

        assertEquals(PairingCodeState.Used, codes.state.value)
    }

    @Test
    fun `stale confirmation does not touch a newer code`() = runTest {
        val codes = codes()
        codes.issue()
        codes.take()
        codes.issue()
        codes.onUsed()

        assertTrue(codes.state.value is PairingCodeState.Active)
    }

    @Test
    fun `code past its deadline is not handed out`() = runTest {
        val codes = codes()
        codes.issue()
        clock.advance(61.seconds)

        assertNull(codes.take())
        assertEquals(PairingCodeState.Expired, codes.state.value)
    }

    @Test
    fun `code expires on its own`() = runTest {
        val codes = codes()
        codes.issue()
        advanceTimeBy(61.seconds)

        assertEquals(PairingCodeState.Expired, codes.state.value)
    }

    @Test
    fun `revoked code is not handed out`() = runTest {
        val codes = codes()
        codes.issue()
        codes.revoke()

        assertNull(codes.take())
        assertEquals(PairingCodeState.Idle, codes.state.value)
    }
}
