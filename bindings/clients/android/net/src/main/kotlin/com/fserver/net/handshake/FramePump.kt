package com.fserver.net.handshake

import com.fserver.net.NetworkException
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
import kotlinx.coroutines.selects.select
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.time.Duration

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
        val watcher = async {
            job.join()
            // Unknown cause: pump closed/finished without errors, so report a generic link loss.
            NetworkException.SessionLinkLost(null) // FIXME: any error inside work somehow wrapped inside this, need to fix
        }

        select {
            work.onAwait {
                watcher.cancel()
                it
            }
            watcher.onAwait { throw it }
        }
    }

    override fun close() {
        job.cancel()
        runCatching { channel.close() }
    }
}
