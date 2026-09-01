package com.fserver.core.sync.index

import com.fserver.core.files.scan.toFiles
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.SourceEntry
import com.fserver.files.FilesNode

internal class LocalChangesIndexer(
    private val store: FServerStorage,
    private val node: FilesNode,
) {
    suspend fun refresh(source: SourceEntry): List<IndexedFile> {
        val savedState = store.index.processedFiles(source.id)

        val actualState = node.scanner
            .scan(source.location.toFiles())
            .result().getOrThrow()

        //TODO:
        // compare saved and actual state
        // save the changes to the store

        return savedState
    }
}
