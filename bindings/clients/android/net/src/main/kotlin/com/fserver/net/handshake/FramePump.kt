package com.fserver.net.handshake

import com.fserver.common.exception.NetworkException
import com.fserver.net.spi.Transport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

/**
 * Collects a transport channel exactly once and buffers what it produces.
 *
 * Without this the handshake and the session would each collect [Transport.Channel.inbound], and a
 * hot transport flow would quietly drop whatever arrived between the two collections.
 */
internal class FramePump(
    scope: CoroutineScope,
    private val channel: Transport.Channel,
) : AutoCloseable {
    private val frames = Channel<ByteArray>(Channel.UNLIMITED)

    private val job = scope.launch {
        try {
            channel.inbound.collect(frames::send)
            frames.close()
        } catch (e: CancellationException) {
            // Without a cause: a pump that was canceled is a link that ended, and the session's
            // read loop must see a completed stream rather than its own cancellation.
            frames.close()
            throw e
        } catch (e: Throwable) {
            frames.close(e)
        }
    }

    /** One frame, for the handshake. Everything after it goes to [remaining]. */
    suspend fun next(timeout: Duration): ByteArray =
        withTimeoutOrNull(timeout) {
            try {
                frames.receive()
            } catch (_: ClosedReceiveChannelException) {
                // Drained after a clean close: nothing more is coming, and nobody said why.
                throw NetworkException.SessionLinkLost(null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Throwable) {
                // The collector in [job] hit an actual transport error; carry it along.
                throw NetworkException.SessionLinkLost(e)
            }
        } ?: throw NetworkException.Handshake("peer went quiet for $timeout")

    /** The rest of the stream, handed to the session. Completes when the link goes down. */
    fun remaining(): Flow<ByteArray> = frames.receiveAsFlow()

    suspend fun send(frame: ByteArray): Result<Unit> = channel.send(frame)

    /**
     * Runs [block], but if the link goes down before it finishes, cancels it and throws instead of
     * leaving it suspended on a pump that will never produce another frame. Used to bound anything
     * that waits on a person (auth confirmation) against the peer hanging up mid-wait.
     */
    suspend fun <T> runOrAbort(block: suspend () -> T): T = coroutineScope {
        val work = async { block() }
        val watcher = launch {
            job.join()
            // The link is down, but what it already delivered is still buffered, and what [block]
            // makes of that is the better answer: a peer that refuses says why and then hangs up,
            // and "link lost" would throw that reason away. So the block gets [ABORT_GRACE] to
            // reach its own conclusion; only a wait that outlives it is one that can no longer end.
            withTimeoutOrNull(ABORT_GRACE) { work.join() }
            work.cancel(LinkLost())
        }

        try {
            work.await()
        } catch (_: LinkLost) {
            // Unknown cause: the pump closed or finished without an error of its own.
            throw NetworkException.SessionLinkLost(null)
        } finally {
            watcher.cancel()
        }
    }

    override fun close() {
        job.cancel()
        runCatching { channel.close() }
    }

    private companion object {
        /** How long a block may keep working on buffered frames after the link went down. */
        val ABORT_GRACE = 500.milliseconds
    }
}

/** Cancels [FramePump.runOrAbort]'s block once the link it is waiting on is gone for good. */
private class LinkLost : CancellationException("the link went down mid-handshake")
