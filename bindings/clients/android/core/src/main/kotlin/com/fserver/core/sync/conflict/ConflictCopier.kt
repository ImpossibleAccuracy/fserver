package com.fserver.core.sync.conflict

import com.fserver.core.files.scan.toFiles
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.model.SourceEntry
import com.fserver.files.FilesNode
import com.fserver.files.upload.FileRecord
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.FileNotFoundException

/** "Keep both": this device's bytes copied next to the file, under a name [ConflictCopies] picks. */
internal class ConflictCopier(
    private val storage: FServerStorage,
    private val node: FilesNode,
) {
    /**
     * Local bytes of [file] copied as `<name> (<this device>).<ext>`. A new file: the next scan
     * versions it, and a pass sends it like any other.
     */
    suspend fun copyAside(file: FileRecord, source: SourceEntry) {
        val locator = file.locator
            ?: error("Cannot copy local file ${file.id} because it has no locator")
        val label = storage.identity.localDevice().displayName

        withContext(Dispatchers.IO) {
            val fs = node.openSource(source.location.toFiles())
            val original = fs.openFile(locator) ?: throw FileNotFoundException(locator)

            val path = generateSequence(1) { it + 1 }
                .map { n -> ConflictCopies.path(file.path, label, n) }
                .first { !fs.fileExists(it) }

            val copy = fs.createFile(path)

            copy.openWriter().use { writer ->
                original.read().use { input ->
                    val buffer = ByteArray(CopyChunkSize)
                    var offset = 0L

                    while (true) {
                        val read = input.read(buffer)
                        if (read == -1) break

                        writer.write(offset, buffer, read)
                        offset += read
                    }
                }

                writer.sync()
            }

            Timber.i("Copied ${file.path} aside to $path in source ${source.id}")
        }
    }

    private companion object {
        const val CopyChunkSize = 64 * 1024
    }
}
