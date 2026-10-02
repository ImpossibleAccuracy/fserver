package com.fserver.app.presentation.shared.viewer.impl

import android.media.MediaDataSource
import com.fserver.core.files.access.SourceFileReader
import kotlinx.coroutines.runBlocking
import okio.Buffer
import okio.Source
import okio.Timeout

/**
 * [reader] for the platform's blocking readers - `MediaMetadataRetriever`, a decoder. Bytes come
 * through `:core`, so an encrypted source reads as plaintext. Closing it closes [reader].
 */
internal class ReaderMediaDataSource(private val reader: SourceFileReader) : MediaDataSource() {
    private val scratch = ByteArray(ChunkSize)

    override fun getSize(): Long = runBlocking { reader.size() }

    override fun readAt(position: Long, buffer: ByteArray, offset: Int, size: Int): Int {
        if (size == 0) return 0
        val read = runBlocking { reader.read(position, scratch, minOf(size, scratch.size)) }
        if (read > 0) scratch.copyInto(buffer, offset, 0, read)
        return read
    }

    override fun close() = reader.close()
}

/** [data] front to back, for Coil decoders that stream. */
internal class MediaDataSourceSource(private val data: MediaDataSource) : Source {
    private val chunk = ByteArray(ChunkSize)
    private var position = 0L

    override fun read(sink: Buffer, byteCount: Long): Long {
        val read = data.readAt(position, chunk, 0, minOf(byteCount, chunk.size.toLong()).toInt())
        if (read <= 0) return -1
        sink.write(chunk, 0, read)
        position += read
        return read.toLong()
    }

    override fun timeout(): Timeout = Timeout.NONE

    override fun close() = data.close()
}

private const val ChunkSize = 64 * 1024
