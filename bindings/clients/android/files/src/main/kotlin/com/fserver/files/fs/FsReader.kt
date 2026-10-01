package com.fserver.files.fs

import java.io.Closeable

/** One open descriptor on an [FsFile], for reads at any offset: a viewer seeks, a stream cannot. */
interface FsReader : Closeable {
    /** The file's size in bytes, as of now. */
    suspend fun size(): Long

    /** Reads up to [length] bytes at [offset] into [bytes]; returns how many, or -1 past the end. */
    suspend fun read(
        offset: Long,
        bytes: ByteArray,
        length: Int = bytes.size,
    ): Int
}
