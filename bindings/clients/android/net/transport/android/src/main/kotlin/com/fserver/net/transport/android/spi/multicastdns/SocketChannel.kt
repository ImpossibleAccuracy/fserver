package com.fserver.net.transport.android.spi.multicastdns

import com.fserver.common.exception.NetworkException
import com.fserver.net.spi.Transport
import com.fserver.net.spi.TransportEndpoint
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.Closeable
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.IOException
import java.net.Socket
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class SocketChannel(
    private val socket: Socket,
    override val endpoint: TransportEndpoint,
    private val maxFrameSize: Int,
) : Transport.Channel {
    private val writeLock = Mutex()
    private val collected = AtomicBoolean(false)

    init {
        SocketTuning.afterConnect(socket)
    }

    // Buffered, and larger than a frame on purpose: BufferedOutputStream writes an array bigger
    // than its buffer straight through, which would flush the 4-byte length on its own and put a
    // runt segment in front of every frame. With room for both, a frame is one write.
    private val inputStream = DataInputStream(BufferedInputStream(socket.getInputStream()))
    private val outputStream = DataOutputStream(
        BufferedOutputStream(socket.getOutputStream(), maxFrameSize + Int.SIZE_BYTES)
    )

    /** Collectable once: a second reader of the same stream would split frames in half. */
    override val inbound: Flow<ByteArray> = flow {
        check(collected.compareAndSet(false, true)) {
            "inbound of ${endpoint.address} is already being collected"
        }

        while (currentCoroutineContext().isActive) {
            val size = try {
                inputStream.readInt()
            } catch (_: IOException) {
                break // Socket closed
            }

            if (size !in 1..maxFrameSize) {
                throw IOException("Invalid frame size: $size")
            }

            val frame = ByteArray(size)
            inputStream.readFully(frame)
            emit(frame)
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Refused here rather than written: the peer's reader rejects an oversized frame and drops the
     * link, so putting one on the wire costs the session instead of one message.
     */
    override suspend fun send(frame: ByteArray): Result<Unit> = writeLock.withLock {
        if (frame.size > maxFrameSize) {
            return@withLock Result.failure(
                NetworkException.FrameTooLarge(size = frame.size, limit = maxFrameSize)
            )
        }

        withContext(Dispatchers.IO) {
            try {
                outputStream.writeInt(frame.size)
                outputStream.write(frame)
                outputStream.flush()

                Result.success(Unit)
            } catch (e: CancellationException) {
                throw e
            } catch (e: IOException) {
                Result.failure(e)
            }
        }
    }

    override fun close() {
        socket.closeQuietly()
    }
}

/** Teardown paths cannot do anything useful with a close failure. */
internal fun Closeable.closeQuietly() {
    try {
        close()
    } catch (_: IOException) {
        // Already closed, or the peer is gone.
    }
}
