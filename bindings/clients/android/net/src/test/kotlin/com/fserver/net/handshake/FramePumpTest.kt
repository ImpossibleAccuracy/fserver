package com.fserver.net.handshake

import com.fserver.common.exception.NetworkException
import com.fserver.net.support.channelPair
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

class FramePumpTest {

    private val scope = CoroutineScope(SupervisorJob())

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `frames that arrive while the handshake is still running are not dropped`() = runBlocking {
        val (ours, theirs) = channelPair()
        val pump = FramePump(scope, ours)

        // All three land before anyone asks for the second and third.
        theirs.send(byteArrayOf(1))
        theirs.send(byteArrayOf(2))
        theirs.send(byteArrayOf(3))

        val handshakeFrame = withTimeout(TIMEOUT) { pump.next(TIMEOUT) }
        val rest = withTimeout(TIMEOUT) { pump.remaining().take(2).toList() }

        assertEquals(1, handshakeFrame.single().toInt())
        assertEquals(listOf(2, 3), rest.map { it.single().toInt() })
    }

    @Test
    fun `the remaining stream ends when the link goes down`() = runBlocking {
        val (ours, theirs) = channelPair()
        val pump = FramePump(scope, ours)

        theirs.send(byteArrayOf(1))
        theirs.close()

        // Completion is how a session learns it lost its link, so it must survive a pending frame.
        val delivered = withTimeout(TIMEOUT) { pump.remaining().toList() }

        assertEquals(listOf(1), delivered.map { it.single().toInt() })
    }

    @Test
    fun `a quiet peer surfaces as a handshake failure, not as a hang`() = runBlocking {
        val (ours, _) = channelPair()
        val pump = FramePump(scope, ours)

        val outcome = runCatching { pump.next(100.milliseconds) }

        assertTrue(outcome.exceptionOrNull() is NetworkException.Handshake)
    }

    @Test
    fun `what the block concluded from buffered frames beats the link going down`() = runBlocking {
        val (ours, theirs) = channelPair()
        val pump = FramePump(scope, ours)

        // What a peer that refuses does: it says why, then hangs up. The reason is already here.
        theirs.send(byteArrayOf(7))
        theirs.close()

        val outcome = runCatching {
            withTimeout(TIMEOUT) {
                pump.runOrAbort {
                    val reason = pump.next(TIMEOUT).single().toInt()
                    // The handshake is not done the instant a frame lands: it still decodes it and
                    // tells the peer why it is failing, and both of those suspend.
                    delay(50)
                    throw NetworkException.Handshake("peer closed the handshake: $reason")
                }
            }
        }

        // Not SessionLinkLost: the link did go down, but "peer said 7" is the better answer.
        assertEquals(
            "peer closed the handshake: 7",
            (outcome.exceptionOrNull() as NetworkException.Handshake).message,
        )
    }

    @Test
    fun `a block that already finished survives the link going down`() = runBlocking {
        val (ours, theirs) = channelPair()
        val pump = FramePump(scope, ours)

        theirs.close()
        runCatching { pump.next(TIMEOUT) }

        assertEquals("done", withTimeout(TIMEOUT) { pump.runOrAbort { "done" } })
    }

    @Test
    fun `a block still waiting when the link goes down is aborted`() = runBlocking {
        val (ours, theirs) = channelPair()
        val pump = FramePump(scope, ours)

        val outcome = runCatching {
            withTimeout(TIMEOUT) {
                pump.runOrAbort {
                    theirs.close()
                    awaitCancellation()
                }
            }
        }

        assertTrue(outcome.exceptionOrNull() is NetworkException.SessionLinkLost)
    }

    private companion object {
        val TIMEOUT = 5.seconds
    }
}
