package com.fserver.core.crypto.format

import com.fserver.common.exception.FileSystemException

/**
 * Where plaintext lands on disk. Every segment but the last holds [segmentSize] plaintext bytes,
 * the last holds the rest - none for an empty file, which still has one. Each segment is its
 * nonce plus sealed bytes, [overhead] over the plaintext.
 */
internal class SealedLayout(
    val headerSize: Int,
    val segmentSize: Int,
    val overhead: Int,
) {
    private val stride = segmentSize.toLong() + overhead

    fun segmentCount(plainSize: Long): Long =
        if (plainSize == 0L) 1 else (plainSize + segmentSize - 1) / segmentSize

    fun lastIndex(plainSize: Long): Long = segmentCount(plainSize) - 1

    /** Plaintext bytes segment [index] holds in a file of [plainSize]. */
    fun plainLength(index: Long, plainSize: Long): Int =
        (plainSize - index * segmentSize).coerceIn(0, segmentSize.toLong()).toInt()

    fun segmentOffset(index: Long): Long = headerSize + index * stride

    fun rawSize(plainSize: Long): Long = headerSize + plainSize + segmentCount(plainSize) * overhead

    /** The inverse of [rawSize]; a length it cannot produce is a damaged file. */
    fun plainSize(rawSize: Long): Long {
        val body = rawSize - headerSize
        if (body < overhead) throw FileSystemException.Corrupted("no segments")

        val full = body / stride
        val rest = body % stride
        return when {
            rest == 0L -> full * segmentSize
            rest < overhead || (rest == overhead.toLong() && full > 0) ->
                throw FileSystemException.Corrupted("partial segment at the end")

            else -> full * segmentSize + rest - overhead
        }
    }
}
