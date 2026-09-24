package com.fserver.files.fs.impl

import com.fserver.files.fs.FsFile
import com.fserver.files.fs.FsWriter
import java.io.ByteArrayInputStream
import java.io.InputStream
import kotlin.time.Instant

/** A file from a backend nobody can rename out of: only its bytes can be read. */
internal class BytesFile(private val content: String) : FsFile {
    var deleted = false
        private set

    override val locator = "test://bytes"

    override suspend fun read(): InputStream = ByteArrayInputStream(content.toByteArray())
    override suspend fun openWriter(): FsWriter = throw UnsupportedOperationException()
    override suspend fun rename(newName: String, deleteOldOnConflict: Boolean): FsFile = this
    override suspend fun delete(): Boolean = true.also { deleted = true }
    override suspend fun settleLastModified(time: Instant): Instant = time
}
