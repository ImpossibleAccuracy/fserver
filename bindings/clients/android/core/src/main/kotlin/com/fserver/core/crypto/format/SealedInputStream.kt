package com.fserver.core.crypto.format

import com.fserver.common.exception.FileSystemException
import java.io.InputStream

/**
 * Plaintext of a sealed file, front to back. [raw] must stand just past the header; [plainSize]
 * comes from the file's length, so a file cut short fails rather than ends early.
 */
internal class SealedInputStream(
    private val raw: InputStream,
    private val file: SealedFile,
    private val plainSize: Long,
) : InputStream() {
    private val layout = file.layout
    private val lastIndex = layout.lastIndex(plainSize)

    private var index = 0L
    private var segment = ByteArray(0)
    private var position = 0

    override fun read(): Int {
        if (!fill()) return -1
        return segment[position++].toInt() and 0xFF
    }

    override fun read(b: ByteArray, off: Int, len: Int): Int {
        if (len == 0) return 0
        if (!fill()) return -1

        val count = minOf(len, segment.size - position)
        segment.copyInto(b, off, position, position + count)
        position += count
        return count
    }

    override fun available(): Int = segment.size - position

    override fun close() = raw.close()

    /** False once every segment was handed out. */
    private fun fill(): Boolean {
        while (position == segment.size) {
            if (index > lastIndex) return false

            val sealed = ByteArray(layout.plainLength(index, plainSize) + layout.overhead)
            var done = 0
            while (done < sealed.size) {
                val read = raw.read(sealed, done, sealed.size - done)
                if (read < 0) throw FileSystemException.Corrupted("segment $index cut short")
                done += read
            }

            segment = file.open(index, sealed, last = index == lastIndex)
            position = 0
            index++
        }
        return true
    }
}
