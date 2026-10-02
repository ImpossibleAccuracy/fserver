package com.fserver.core.crypto.internal

import android.os.ParcelFileDescriptor
import com.fserver.common.exception.FileSystemException
import com.fserver.core.crypto.format.SealedHeader
import com.fserver.core.crypto.format.SealedInputStream
import com.fserver.core.crypto.model.AtRest
import com.fserver.files.fs.FsFile
import com.fserver.files.fs.FsReader
import com.fserver.files.fs.FsWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStream
import kotlin.time.Instant

/** A sealed [raw] file, seen as its plaintext. [header] is as read when it was opened. */
internal class SealedFsFile(
    val raw: FsFile,
    val header: SealedHeader,
    private val files: SealedFiles,
) : FsFile {
    override val locator: String get() = raw.locator

    val atRest: AtRest.Sealed get() = AtRest.Sealed(cipherId = header.cipherId, keyId = header.keyId)

    override suspend fun read(): InputStream {
        val (file, size) = raw.openReader().use { reader ->
            val file = files.open(reader)
            file to file.layout.plainSize(reader.size())
        }

        val stream = raw.read()
        try {
            withContext(Dispatchers.IO) { stream.skipExactly(file.layout.headerSize.toLong()) }
            return SealedInputStream(stream, file, size)
        } catch (e: Throwable) {
            stream.close()
            throw e
        }
    }

    override suspend fun openReader(): FsReader {
        val reader = raw.openReader()
        return try {
            files.open(reader).reader(reader)
        } catch (e: Throwable) {
            reader.close()
            throw e
        }
    }

    /** No descriptor shows plaintext: another app gets [openReader]'s bytes through a pipe instead. */
    override suspend fun openDescriptor(): ParcelFileDescriptor? = null

    override suspend fun openWriter(): FsWriter {
        val reader = raw.openReader()
        return try {
            val file = files.open(reader)
            file.writer(reader, raw.openWriter())
        } catch (e: Throwable) {
            reader.close()
            throw e
        }
    }

    override suspend fun rename(newName: String, deleteOldOnConflict: Boolean): FsFile =
        SealedFsFile(raw.rename(newName, deleteOldOnConflict), header, files)

    override suspend fun delete(): Boolean = raw.delete()

    override suspend fun settleLastModified(time: Instant): Instant = raw.settleLastModified(time)
}

private fun InputStream.skipExactly(count: Long) {
    var left = count
    while (left > 0) {
        val skipped = skip(left)
        if (skipped > 0) {
            left -= skipped
        } else if (read() >= 0) {
            left--
        } else {
            throw FileSystemException.Corrupted("ends inside the header")
        }
    }
}
