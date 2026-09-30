package com.fserver.core.oneshot.impl

import com.fserver.common.exception.FileSystemException
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.files.SourceLocation
import com.fserver.core.files.scan.toFiles
import com.fserver.core.oneshot.model.OneShotTransfer
import com.fserver.core.oneshot.model.OneShotTransferFile
import com.fserver.files.FilesNode
import com.fserver.files.fs.FsFile
import com.fserver.files.fs.ReadableSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import timber.log.Timber

/**
 * Copies of shared files until their transfer ends: `{transferId}_{index}` in [Location]. A share
 * sheet's grant ends with its task, so a transfer sends its own copy and survives a restart.
 */
internal class OneShotOutbox(private val node: FilesNode) {
    private val outbox get() = node.openSource(Location.toFiles())

    /** Copies the files behind [uris] into [transferId]'s outbox. Fails on the first one unreadable. */
    suspend fun fill(transferId: String, uris: List<String>): List<OneShotTransferFile> {
        val shared = node.openSource(ReadableSource.Shared(uris))

        return try {
            shared.scan().result().getOrThrow().mapIndexed { index, found ->
                val original = shared.openFile(found.locator) ?: throw FileSystemException.InvalidPath(found.locator)
                val copy = outbox.createFile(pathOf(transferId, index))

                OneShotTransferFile(
                    index = index,
                    name = found.path,
                    size = copyBytes(original, copy),
                    locator = copy.locator,
                )
            }
        } catch (e: Throwable) {
            discard(transferId)
            throw e
        }
    }

    /** Drops [transfer]'s copies, if it sends from here. */
    suspend fun release(transfer: OneShotTransfer) {
        val direction = transfer.direction as? OneShotTransfer.Direction.Outgoing ?: return
        if (direction.origin == Location) discard(transfer.id)
    }

    /** Best effort: GC gets whatever this misses. */
    private suspend fun discard(transferId: String) {
        runCatchingCancellable {
            val outbox = outbox

            outbox.scan().result().getOrThrow()
                .filter { transferIdOf(it.path) == transferId }
                .forEach { outbox.openFile(it.locator)?.delete() }
        }.onFailure { Timber.w(it, "Could not discard outbox of transfer $transferId") }
    }

    /** @return bytes copied */
    private suspend fun copyBytes(from: FsFile, to: FsFile): Long = withContext(Dispatchers.IO) {
        from.read().use { input ->
            to.openWriter().use { writer ->
                val buffer = ByteArray(CopyBufferSize)
                var offset = 0L

                while (true) {
                    val read = input.read(buffer)
                    if (read == -1) break

                    writer.write(offset, buffer, read)
                    offset += read
                }

                writer.sync()
                offset
            }
        }
    }

    companion object {
        /** Origin of every transfer of shared files. */
        val Location = SourceLocation.Internal("oneshot-outbox")

        private const val CopyBufferSize = 256 * 1024

        // Flat: the internal bucket leaves the directories of deleted files behind.
        fun pathOf(transferId: String, index: Int) = "${transferId}_$index"

        /** The transfer an outbox [path] belongs to, or null when it is not one of its copies. */
        fun transferIdOf(path: String): String? =
            path.substringBeforeLast('_', missingDelimiterValue = "")
                .takeIf { it.isNotEmpty() && path.substringAfterLast('_').toIntOrNull() != null }
    }
}
