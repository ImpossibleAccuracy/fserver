package com.fserver.core.sync.server.handler.upload

import com.fserver.common.exception.TransferException
import com.fserver.common.model.ContentHash
import com.fserver.common.utils.StageTimer
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.dto.UploadKey
import com.fserver.core.sync.progress.FileTransfer
import com.fserver.core.sync.progress.impl.SyncProgressReporter
import com.fserver.core.sync.transfer.ProgressiveHash
import com.fserver.core.sync.transfer.skipExactly
import com.fserver.files.fs.FsFile
import com.fserver.files.fs.FsWriter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Instant

/**
 * One upload in flight, written into [staging] at the offsets the peer sends and hashed as the
 * run from offset 0 grows, so the hash is ready when the last chunk lands.
 *
 * Bytes are written by [writing], not by the session collector: `:net` drops inbound frames while
 * `incoming` is not drained (PeerSessionImpl), so a disk write on the collector costs us the
 * chunks arriving behind it. [offer] therefore never blocks - a chunk that does not fit the
 * session's buffer is refused, and the peer resends it.
 */
internal class UploadContext(
    val key: UploadKey,
    /** Where the file goes once whole, and what records it. */
    val landing: UploadLanding,
    val staging: FsFile,
    /** Held open for the whole upload: one descriptor, not one per chunk. */
    private val out: FsWriter,
    /** Bytes an earlier attempt left flushed in [staging]. */
    committed: Long,
    val startedAt: Instant,
    private val buffered: AtomicInteger,
    private val progress: SyncProgressReporter,
    scope: CoroutineScope,
) {
    /**
     * Where the receiving side's time goes: waiting for chunks or the disk. A dominant
     * `idle-waiting-for-chunk` means the sender or the link is the limit, not this device.
     */
    private val timer = StageTimer("download $key")

    /** What the writer died of, if it did. Read by the collector, so kept visible to it. */
    @Volatile
    var failure: Throwable? = null
        private set

    /** Fed in offset order; only the writer touches it. Trails [prefix] only inside one [write]. */
    private val digest = ProgressiveHash()

    private val outClosed = AtomicBoolean(false)

    /** Guarded by itself: the writer adds, the collector reads [prefix] for a checkpoint. */
    private val received = ReceivedRanges(committed)

    /** Everything below it is written. Not yet flushed - see [UploadStaging.checkpoint]. */
    val prefix: Long
        get() = synchronized(received) { received.prefix }

    val isWhole: Boolean
        get() = prefix >= landing.size

    private val chunks = Channel<FileServerMessages.UploadChunk>(Channel.UNLIMITED)

    private val writing = scope.launch {
        try {
            write()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Kept rather than thrown: this runs on the session's scope, and one failed upload
            // must not tear down the session serving every other request from this peer.
            failure = e
        } finally {
            chunks.close()
            release()
        }
    }

    /**
     * Queues [chunk] for the writer. False when the buffer is full or the writer is gone.
     *
     * @return true if the chunk was queued, false if it was dropped.
     */
    fun offer(chunk: FileServerMessages.UploadChunk): Boolean {
        val size = chunk.bytes.size

        if (buffered.addAndGet(size) > SessionUploads.InFlightChunkBytesLimit) {
            buffered.addAndGet(-size)
            return false
        }

        if (chunks.trySend(chunk).isSuccess) return true

        buffered.addAndGet(-size)
        return false
    }

    /** Waits for every queued chunk to reach disk, failing with whatever the writer failed with. */
    suspend fun await() {
        chunks.close()
        writing.join()

        Timber.i(timer.summary())

        failure?.let { throw it }
    }

    /** Hash of `[0, prefix)`. Once, after [await] returned and the upload [isWhole]. */
    fun hash(): ContentHash = digest.hash

    /**
     * Flushes what the writer wrote.
     * @return the prefix that is now safe to record.
     */
    suspend fun flush(): Long {
        val flushed = prefix
        out.sync()
        return flushed
    }

    /** Stops the writer, drops its queue and closes the descriptor. The staged bytes stay. Safe to repeat. */
    suspend fun stop() {
        writing.cancelAndJoin()
        release()
        if (outClosed.compareAndSet(false, true)) withContext(Dispatchers.IO) {
            out.close()
        }
    }

    /** [stop], reported as a transfer that did not finish. */
    suspend fun close() {
        stop()
        progress.uploadFailed(FileTransfer.Direction.Incoming, key, failure)
    }

    private suspend fun write() {
        // An earlier attempt's bytes are hashed from disk before anything new is added to them.
        timer.time("hash-resume") { hashStaged(until = prefix) }

        while (true) {
            val chunk = timer.time("idle-waiting-for-chunk") {
                chunks.receiveCatching().getOrNull()
            } ?: break

            try {
                write(chunk)
            } finally {
                give(chunk.bytes.size)
            }
        }
    }

    /** Written where it says, in whatever order it arrives: the ranges say what is still missing. */
    private suspend fun write(chunk: FileServerMessages.UploadChunk) {
        val start = chunk.offset
        val end = start + chunk.bytes.size

        // Bounded by what Init declared, or a peer picks how big a sparse file we make.
        if (start < 0 || end > landing.size) {
            throw TransferException.ChunkOutOfBoundsException(start, chunk.bytes.size, landing.size)
        }

        if (synchronized(received) { received.covers(start, end) }) {
            timer.count("resent-chunks")
            return
        }

        timer.time("disk-write") { out.write(offset = start, bytes = chunk.bytes) }

        val (total, contiguous) = synchronized(received) {
            received.add(start, end)
            received.total to received.prefix
        }

        timer.time("hash") { advanceHash(chunk, contiguous) }

        // Incoming whoever asked: a peer pushing to us, or a download this device requested.
        progress.uploadAdvanced(FileTransfer.Direction.Incoming, key, total)

        timer.count("bytes", chunk.bytes.size.toLong())
        timer.count("chunks")
    }

    /**
     * Hashes up to [contiguous]: from [chunk] while it sits right at where the hash got to, from
     * disk for the run behind it that arrived early.
     */
    private suspend fun advanceHash(chunk: FileServerMessages.UploadChunk, contiguous: Long) {
        digest.feed(chunk.offset, chunk.bytes, chunk.bytes.size)
        hashStaged(until = contiguous)
    }

    /** Feeds `[hashedTo, until)` from [staging] into the hash. */
    private suspend fun hashStaged(until: Long) {
        if (until <= digest.hashedTo) return

        staging.read().use { input ->
            withContext(Dispatchers.IO) {
                input.skipExactly(digest.hashedTo)

                val buffer = ByteArray(HashBufferSize)
                while (digest.hashedTo < until) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), until - digest.hashedTo).toInt())
                    if (read == -1) throw TransferException.FileNotFoundException("Staged $key ends at ${digest.hashedTo}")

                    digest.feed(digest.hashedTo, buffer, read)
                }
            }
        }
    }

    /** Hands the session's buffer back what the queue still holds. Safe to repeat. */
    private fun release() {
        while (true) {
            val chunk = chunks.tryReceive().getOrNull() ?: break
            give(chunk.bytes.size)
        }
    }

    private fun give(bytes: Int) {
        buffered.addAndGet(-bytes)
    }

    private companion object {
        const val HashBufferSize = 1024 * 1024 // 1 MiB
    }
}
