package com.fserver.core.files.access

/** Positional writes into a [SourceFile]; nothing is truncated. */
interface SourceFileWriter {
    suspend fun write(offset: Long, bytes: ByteArray, length: Int = bytes.size)
}
