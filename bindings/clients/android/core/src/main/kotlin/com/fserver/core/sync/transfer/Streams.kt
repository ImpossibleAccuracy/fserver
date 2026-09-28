package com.fserver.core.sync.transfer

import java.io.EOFException
import java.io.InputStream

/**
 * Fills [buffer] up to [length], or to the end of the stream. A single read may return less than
 * asked, and every short read would be a frame carrying less than it could.
 */
internal fun InputStream.fill(buffer: ByteArray, length: Int = buffer.size): Int {
    var filled = 0

    while (filled < length) {
        val read = read(buffer, filled, length - filled)
        if (read == -1) break
        filled += read
    }

    return filled
}

/** Skips up to [count] bytes, fewer only at the end of the stream. */
internal fun InputStream.skipFully(count: Long): Long {
    var skipped = 0L

    while (skipped < count) {
        val step = skip(count - skipped)

        if (step > 0) {
            skipped += step
            continue
        }

        // skip() may return 0 before the end: one read tells the two apart.
        if (read() == -1) break
        skipped++
    }

    return skipped
}

/** Skips exactly [count] bytes. `InputStream.skipNBytes` is API 33+ / JVM 12+. */
internal fun InputStream.skipExactly(count: Long) {
    val skipped = skipFully(count)
    if (skipped < count) throw EOFException("Ended ${count - skipped} bytes short")
}
