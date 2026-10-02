package com.fserver.core.crypto.format

import com.fserver.files.fs.FsReader
import com.fserver.files.fs.FsWriter

/**
 * Plaintext writes at any offset over a sealed file. Every [write] reseals the segments it touches
 * before returning - [close] is not suspend, so nothing may wait for it - and keeps the last one
 * in memory, so appends do not read it back. Writes aligned to the segment size are cheapest.
 */
internal class SealedWriter(
    private val raw: FsReader,
    private val out: FsWriter,
    private val file: SealedFile,
) : FsWriter {
    private val layout = file.layout
    private val segmentSize = layout.segmentSize.toLong()

    private var size: Long = -1
    private var cached: Segment? = null

    override suspend fun write(offset: Long, bytes: ByteArray, length: Int) {
        require(offset >= 0 && length in 0..bytes.size) { "Bad write: $length bytes at $offset" }
        if (length == 0) return

        val oldSize = size()
        val end = offset + length
        val newSize = maxOf(oldSize, end)

        // Growing moves the last-segment mark, so the old last one is resealed even when untouched,
        // along with any gap up to [offset].
        val first = if (newSize > oldSize) minOf(
            offset / segmentSize,
            layout.lastIndex(oldSize)
        ) else offset / segmentSize
        val last = (end - 1) / segmentSize
        for (index in first..last) {
            val start = index * segmentSize
            val from = maxOf(offset, start)
            val to = minOf(end, start + segmentSize)
            val covered = from == start && to - start >= layout.plainLength(index, oldSize)

            val plain = if (covered) ByteArray(segmentSize.toInt()) else load(index, oldSize)
            if (from < to) bytes.copyInto(
                plain,
                (from - start).toInt(),
                (from - offset).toInt(),
                (to - offset).toInt()
            )
            store(
                index,
                plain,
                layout.plainLength(index, newSize),
                last = index == layout.lastIndex(newSize)
            )
        }
        size = newSize
    }

    override suspend fun truncate(size: Long) {
        require(size >= 0) { "Bad size: $size" }
        val oldSize = size()
        if (size >= oldSize) return

        val index = layout.lastIndex(size)
        val plain = load(index, oldSize)
        store(index, plain, layout.plainLength(index, size), last = true)
        out.truncate(layout.rawSize(size))
        this.size = size
    }

    override suspend fun sync() = out.sync()

    override fun close() {
        try {
            out.close()
        } finally {
            raw.close()
        }
    }

    private suspend fun size(): Long {
        if (size < 0) size = layout.plainSize(raw.size())
        return size
    }

    /** Segment [index] of a file of [size] bytes, as a full-size buffer zero-padded past its end. */
    private suspend fun load(index: Long, size: Long): ByteArray {
        val plain = ByteArray(segmentSize.toInt())
        if (index > layout.lastIndex(size)) return plain

        val length = layout.plainLength(index, size)
        val opened = cached?.takeIf { it.index == index && it.length == length }?.plain
            ?: file.open(
                index,
                raw.readFully(layout.segmentOffset(index), length + layout.overhead),
                last = index == layout.lastIndex(size),
            )
        opened.copyInto(plain, 0, 0, length)
        return plain
    }

    private suspend fun store(index: Long, plain: ByteArray, length: Int, last: Boolean) {
        // A stale copy must not outlive a failed write.
        cached = null
        out.write(layout.segmentOffset(index), file.seal(index, plain, length, last))
        cached = Segment(index, length, plain.copyOf(length))
    }
}

private class Segment(val index: Long, val length: Int, val plain: ByteArray)
