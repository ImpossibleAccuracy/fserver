package com.fserver.net.handshake

import com.fserver.net.NetworkException
import com.fserver.net.spi.Transport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
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
        withTimeoutOrNull(timeout) { frames.receive() }
            ?: throw NetworkException.Handshake("peer went quiet for $timeout")

    /** The rest of the stream, handed to the session. Completes when the link goes down. */
    fun remaining(): Flow<ByteArray> = frames.receiveAsFlow()

    suspend fun send(frame: ByteArray): Result<Unit> = channel.send(frame)

    override fun close() {
        job.cancel()
        runCatching { channel.close() }
    }
}
