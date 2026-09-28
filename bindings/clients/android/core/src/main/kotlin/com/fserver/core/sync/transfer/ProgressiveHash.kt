package com.fserver.core.sync.transfer

import com.fserver.common.model.ContentHash
import com.fserver.core.files.util.FileHasher

/**
 * A file's hash, fed each byte once and in offset order however often the bytes are re-read or
 * arrive out of order. A run that starts past [hashedTo] is ignored: the gap has to be fed first.
 */
internal class ProgressiveHash {
    private val hasher = FileHasher()

    var hashedTo = 0L
        private set

    /** Computed once; feed nothing after. */
    val hash: ContentHash by lazy { hasher.compute() }

    fun feed(position: Long, buffer: ByteArray, length: Int) {
        val end = position + length
        if (position > hashedTo || end <= hashedTo) return

        val from = (hashedTo - position).toInt()
        hasher.write(buffer, from, length - from)
        hashedTo = end
    }
}
