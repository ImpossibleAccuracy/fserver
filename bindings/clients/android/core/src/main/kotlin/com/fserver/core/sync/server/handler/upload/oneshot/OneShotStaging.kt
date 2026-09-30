package com.fserver.core.sync.server.handler.upload.oneshot

import com.fserver.common.exception.FileSystemException
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.files.FilesNode
import com.fserver.files.fs.FsFile
import timber.log.Timber

/**
 * Where an incoming one-shot file waits until whole: `oneshot/{transferId}/{index}` in
 * [FilesNode.openStaging]. Resume offset lives on the transfer's own record, not here.
 */
internal class OneShotStaging(private val node: FilesNode) {
    private val staging get() = node.openStaging()

    /**
     * The staged file, keeping [committed] bytes when they are still there. Returns how many were
     * kept: 0 when the cache was cleared since, and the sender starts over.
     */
    suspend fun open(transferId: String, index: Int, committed: Long): Pair<FsFile, Long> {
        // Peer-chosen, and becomes a directory name.
        requireSegment(transferId)

        val path = pathOf(transferId, index)

        if (committed > 0) {
            find(path)?.let { return it to committed }
        }

        find(path)?.delete()
        return staging.createFile(path) to 0L
    }

    /** Drops what [transferId] staged. Best effort: GC gets whatever this misses. */
    suspend fun discard(transferId: String) {
        runCatchingCancellable {
            staging.scan().result().getOrThrow()
                .filter { transferIdOf(it.path) == transferId }
                .forEach { staging.openFile(it.locator)?.delete() }
        }.onFailure { Timber.w(it, "Could not discard staging of transfer $transferId") }
    }

    private suspend fun find(path: String): FsFile? =
        staging.scan().result().getOrThrow()
            .find { it.path == path }
            ?.let { staging.openFile(it.locator) }

    companion object {
        private const val Directory = "oneshot"

        fun pathOf(transferId: String, index: Int) = "$Directory/$transferId/$index"

        /** The transfer a staged [path] belongs to, or null when it is not a one-shot file. */
        fun transferIdOf(path: String): String? {
            val segments = path.split('/')
            return if (segments.size == 3 && segments[0] == Directory) segments[1] else null
        }

        private fun requireSegment(id: String) {
            if (id.isEmpty() || id == "." || id == ".." || id.any { it == '/' || it == '\\' }) {
                throw FileSystemException.InvalidPath(id)
            }
        }
    }
}
