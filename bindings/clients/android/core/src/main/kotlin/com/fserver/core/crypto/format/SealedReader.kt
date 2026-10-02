package com.fserver.core.crypto.format

import com.fserver.files.fs.FsReader

/** Plaintext reads at any offset over a sealed [raw] file. Keeps the last segment it opened. */
internal class SealedReader(
    private val raw: FsReader,
    private val file: SealedFile,
) : FsReader {
    private val layout = file.layout
    private var cached: OpenedSegment? = null

    override suspend fun size(): Long = layout.plainSize(raw.size())

    override suspend fun read(offset: Long, bytes: ByteArray, length: Int): Int {
        require(offset >= 0 && length in 0..bytes.size) { "Bad read: $length bytes at $offset" }

        val size = size()
        if (offset >= size) return -1

        val end = minOf(size, offset + length)
        var position = offset
        while (position < end) {
            val index = position / layout.segmentSize
            val plain = segment(index, size)
            val from = (position - index * layout.segmentSize).toInt()
            val count = minOf(plain.size - from, (end - position).toInt())
            plain.copyInto(bytes, (position - offset).toInt(), from, from + count)
            position += count
        }
        return (end - offset).toInt()
    }

    override fun close() = raw.close()

    private suspend fun segment(index: Long, size: Long): ByteArray {
        val last = index == layout.lastIndex(size)
        cached?.takeIf { it.index == index && it.last == last && it.size == size }
            ?.let { return it.plain }

        val sealed = raw.readFully(
            layout.segmentOffset(index),
            layout.plainLength(index, size) + layout.overhead
        )
        return file.open(index, sealed, last).also { cached = OpenedSegment(index, last, size, it) }
    }
}

/** A segment as opened while the file was [size] bytes: a resized file may hold another one there. */
private class OpenedSegment(
    val index: Long,
    val last: Boolean,
    val size: Long,
    val plain: ByteArray
)
