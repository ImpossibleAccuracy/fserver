package com.fserver.files.fs.impl

import com.fserver.files.fs.FileSystem
import com.fserver.files.fs.FsFile
import com.fserver.files.fs.FsWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext
import java.io.OutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel

/** A file that takes its whole content as one stream: one open for a copy, not one per chunk. */
internal interface StreamTarget {
    /** Truncates the file and opens it for writing from the start. */
    suspend fun openOutput(): OutputStream
}

/** [FsWriter] over one [channel]; [onClose] releases whatever owns its descriptor. */
internal class ChannelWriter(
    private val channel: FileChannel,
    private val onClose: () -> Unit = channel::close,
) : FsWriter {
    override suspend fun write(offset: Long, bytes: ByteArray, length: Int) =
        withContext(Dispatchers.IO) {
            val buffer = ByteBuffer.wrap(bytes, 0, length)
            var written = 0L

            while (buffer.hasRemaining()) {
                written += channel.write(buffer, offset + written)
            }
        }

    override suspend fun sync() = withContext(Dispatchers.IO) { channel.force(false) }

    override fun close() = onClose()
}

/**
 * [FileSystem.place] by copy: [file] into [part] beside the target, [replace] puts [part] over it,
 * then [file] goes. A failure leaves [file] as it was and drops [part].
 */
internal suspend fun <T> placeByCopy(
    file: FsFile,
    part: T,
    replace: suspend (T) -> FsFile,
): FsFile where T : FsFile, T : StreamTarget {
    val placed = try {
        file.read().use { input ->
            part.openOutput().use { output ->
                withContext(Dispatchers.IO) { input.copyTo(output, CopyBufferSize) }
            }
        }

        replace(part)
    } catch (e: Throwable) {
        withContext(NonCancellable) { part.delete() }
        throw e
    }

    // One the backend refuses to delete stays behind as a copy nothing points at.
    file.delete()

    return placed
}

/** Big enough that a copy is bound by the disk, small enough to be a constant cost. */
internal const val CopyBufferSize = 1024 * 1024 // 1 MiB
