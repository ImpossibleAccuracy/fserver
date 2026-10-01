package com.fserver.core.files.access

import java.io.Closeable

/** Positional reads from a [SourceFile], for a caller that seeks. The caller closes it. */
interface SourceFileReader : Closeable {
    /** The file's size in bytes, as of now. */
    suspend fun size(): Long

    /** Reads up to [length] bytes at [offset] into [bytes]; returns how many, or -1 past the end. */
    suspend fun read(offset: Long, bytes: ByteArray, length: Int = bytes.size): Int
}
