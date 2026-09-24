package com.fserver.files.fs

import java.io.Closeable

/** One open descriptor on an [FsFile], for a run of writes. Positional, so chunks may come in any order. */
interface FsWriter : Closeable {
    /** Write [bytes] starting at [offset]. Throws if nothing was written. */
    suspend fun write(
        offset: Long,
        bytes: ByteArray,
        length: Int = bytes.size,
    )

    /** Flushes what was written to the storage device, so it outlives a crash. */
    suspend fun sync()
}
