package com.fserver.core.sync.server.handler.upload

import com.fserver.common.exception.TransferException
import com.fserver.common.model.ContentHash
import com.fserver.common.utils.StageTimer
import com.fserver.core.files.util.FileHasher
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.progress.FileTransferKey
import com.fserver.core.sync.progress.SyncProgressReporter
import com.fserver.core.sync.server.SessionContext
import com.fserver.files.fs.FileSystem
import com.fserver.files.fs.FsFile
import com.fserver.files.fs.FsWriter
import com.fserver.files.upload.FileRecord
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
    val file: FileRecord,
    val key: IndexedFileKey,
    /** Where [file] goes once whole. */
    val fs: FileSystem,
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
    /** Incoming whoever asked: a peer pushing to us, or a download this device requested. */
    val transferKey: FileTransferKey = SyncProgressReporter.incoming(key.sourceId, key.fileId)

    /**
     * Where the receiving side's time goes: waiting for chunks or the disk. A dominant
     * `idle-waiting-for-chunk` means the sender or the link is the limit, not this device.
     */
    private val timer = StageTimer("download ${file.id}")

    /** What the writer died of, if it did. Read by the collector, so kept visible to it. */
    @Volatile
    var failure: Throwable? = null
        private set

    /** Fed in offset order; only the writer touches it. */
    private val hasher = FileHasher()

    /** Where [hasher] got to. Trails [prefix] only inside one [write]. */
    private var hashedTo = 0L

    private val outClosed = AtomicBoolean(false)

    /** Guarded by itself: the writer adds, the collector reads [prefix] for a checkpoint. */
    private val received = ReceivedRanges(committed)

    /** Everything below it is written. Not yet flushed - see [UploadStaging.checkpoint]. */
    val prefix: Long
        get() = synchronized(received) { received.prefix }

    val isWhole: Boolean
        get() = prefix >= file.metadata.size

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

        if (buffered.addAndGet(size) > SessionContext.InFlightChunkBytesLimit) {
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
    fun hash(): ContentHash = hasher.compute()

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
        progress.transferFailed(transferKey, failure)
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
        if (start < 0  || end > file.metadata.size) {
            throw TransferException.ChunkOutOfBoundsException(start, chunk.bytes.size, file.metadata.size)
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

        progress.transferAdvanced(transferKey, total)

        timer.count("bytes", chunk.bytes.size.toLong())
        timer.count("chunks")
    }

    /**
     * Hashes up to [contiguous]: from [chunk] while it sits right at [hashedTo], from disk for the
     * run behind it that arrived early.
     */
    private suspend fun advanceHash(chunk: FileServerMessages.UploadChunk, contiguous: Long) {
        val end = chunk.offset + chunk.bytes.size

        if (chunk.offset <= hashedTo && hashedTo < end) {
            val from = (hashedTo - chunk.offset).toInt()
            hasher.write(chunk.bytes, from, chunk.bytes.size - from)
            hashedTo = end
        }

        hashStaged(until = contiguous)
    }

    /** Feeds `[hashedTo, until)` from [staging] into the hash. */
    private suspend fun hashStaged(until: Long) {
        if (until <= hashedTo) return

        staging.read().use { input ->
            withContext(Dispatchers.IO) {
                input.skipNBytesCompat(hashedTo)

                val buffer = ByteArray(HashBufferSize)
                while (hashedTo < until) {
                    val read = input.read(buffer, 0, minOf(buffer.size.toLong(), until - hashedTo).toInt())
                    if (read == -1) throw TransferException.FileNotFoundException("Staged ${file.id} ends at $hashedTo")

                    hasher.write(buffer, 0, read)
                    hashedTo += read
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

/** `InputStream.skipNBytes` is API 33+ / JVM 12+. */
private fun java.io.InputStream.skipNBytesCompat(count: Long) {
    var left = count

    while (left > 0) {
        val skipped = skip(left)

        if (skipped > 0) {
            left -= skipped
            continue
        }

        // skip() may return 0 before the end: one read tells the two apart.
        if (read() == -1) throw java.io.EOFException("Ended $left bytes short")
        left--
    }
}
