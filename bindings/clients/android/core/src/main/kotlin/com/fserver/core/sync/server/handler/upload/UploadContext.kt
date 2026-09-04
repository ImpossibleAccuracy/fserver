package com.fserver.core.sync.server.handler.upload

import com.fserver.common.utils.StageTimer
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.files.util.FileHasher
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.progress.FileTransferKey
import com.fserver.core.sync.progress.SyncProgressReporter
import com.fserver.core.sync.server.SessionContext
import com.fserver.files.fs.FileSystem
import com.fserver.files.upload.FileRecord
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Instant

/**
 * One upload in flight.
 *
 * Bytes are written by [writer], not by the session collector: `:net` drops inbound frames while
 * `incoming` is not drained (PeerSessionImpl), so a disk write on the collector costs us the
 * chunks arriving behind it. [offer] therefore never blocks - a chunk that does not fit the
 * session's buffer is refused, and the peer resends it.
 */
internal class UploadContext(
    val file: FileRecord,
    val startedAt: Instant,
    key: IndexedFileKey,
    private val fs: FileSystem,
    private val buffered: AtomicInteger,
    private val progress: SyncProgressReporter,
    scope: CoroutineScope,
) {
    val hasher = FileHasher()

    /** Incoming whoever asked: a peer pushing to us, or a download this device requested. */
    val transferKey: FileTransferKey = SyncProgressReporter.incoming(key.sourceId, key.fileId)

    /**
     * Where the receiving side's time goes: waiting for chunks, the disk, or hashing. A dominant
     * `idle-waiting-for-chunk` means the sender or the link is the limit, not this device.
     */
    private val timer = StageTimer("download ${file.id}")

    /** Where the bytes landed. Meaningful once [await] returned. */
    var locator: String? = null
        private set

    /** What the writer died of, if it did. Read by the collector, so kept visible to it. */
    @Volatile
    var failure: Throwable? = null
        private set

    private val chunks = Channel<FileServerMessages.UploadChunk>(Channel.UNLIMITED)

    /** Chunks that arrived before the gap in front of them was filled, keyed by offset. */
    private val outOfOrder = HashMap<Long, FileServerMessages.UploadChunk>()

    private val writer = scope.launch {
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
        writer.join()

        Timber.i(timer.summary())

        failure?.let { throw it }
    }

    /** Drops the writer, the queue, and the half-written file nothing will point at. */
    suspend fun abandon() {
        writer.cancelAndJoin()
        release()
        progress.transferFailed(transferKey, failure)

        val written = locator ?: return

        runCatchingCancellable { fs.deleteFile(written) }
            .onFailure { Timber.w(it, "Cannot delete the partial upload of ${file.path}") }
    }

    /**
     * Writes chunks in offset order, holding the ones that overtook the gap in front of them.
     *
     * A loop rather than recursion: the buffer is bounded in bytes, not in chunks, so draining it
     * a frame at a time is a stack overflow a peer gets to choose the depth of.
     */
    private suspend fun write() {
        var offset = 0L

        while (true) {
            val chunk = timer.time("idle-waiting-for-chunk") {
                chunks.receiveCatching().getOrNull()
            } ?: break

            if (chunk.offset < offset) {
                // Already on disk: the peer resent it.
                give(chunk.bytes.size)
                timer.count("resent-chunks")
                continue
            }

            outOfOrder.put(chunk.offset, chunk)?.let {
                give(it.bytes.size)
                timer.count("duplicate-chunks")
            }

            while (true) {
                val next = outOfOrder.remove(offset) ?: break

                // Created on the first chunk, so an upload that never sends one leaves no file.
                val target = locator ?: timer.time("create-file") {
                    fs.createFile(file.path).also { locator = it }
                }

                timer.time("disk-write") {
                    fs.writeFile(locator = target, offset = next.offset, bytes = next.bytes)
                }

                timer.time("hash") { hasher.write(next.bytes, next.bytes.size) }

                offset += next.bytes.size
                give(next.bytes.size)
                progress.transferAdvanced(transferKey, offset)

                timer.count("bytes", next.bytes.size.toLong())
                timer.count("chunks")
            }
        }
    }

    /** Hands the session's buffer back what nothing holds anymore. Safe to repeat. */
    private fun release() {
        while (true) {
            val chunk = chunks.tryReceive().getOrNull() ?: break
            give(chunk.bytes.size)
        }

        outOfOrder.values.forEach { give(it.bytes.size) }
        outOfOrder.clear()
    }

    private fun give(bytes: Int) {
        buffered.addAndGet(-bytes)
    }
}