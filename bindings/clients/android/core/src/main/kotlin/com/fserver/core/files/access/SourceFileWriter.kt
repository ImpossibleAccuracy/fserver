package com.fserver.core.files.access

/** Positional writes into a [SourceFile]; nothing is truncated unless [truncate] says so. */
interface SourceFileWriter {
    suspend fun write(offset: Long, bytes: ByteArray, length: Int = bytes.size)

    /** Cuts the file down to [size] bytes; a larger [size] changes nothing. */
    suspend fun truncate(size: Long)
}
