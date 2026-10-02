package com.fserver.core.crypto.internal

import com.fserver.core.crypto.internal.fs.EncryptedFileSystem
import com.fserver.core.crypto.internal.fs.PlainSourceFileSystem
import com.fserver.core.crypto.internal.fs.SourceFileSystem
import com.fserver.core.crypto.model.supportsEncryption
import com.fserver.core.files.scan.toFiles
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.model.SourceEntry
import com.fserver.files.FilesNode

/**
 * The one way to a source's files. Every location that may hold sealed files goes through
 * [com.fserver.core.crypto.internal.fs.EncryptedFileSystem] whatever its policy: files sealed under an earlier one stay readable.
 */
internal class SourceFileSystems(
    private val node: FilesNode,
    private val storage: FServerStorage,
    private val sealedFiles: SealedFiles,
) {
    fun open(source: SourceEntry): SourceFileSystem {
        val inner = node.openSource(source.location.toFiles())
        if (!source.location.supportsEncryption) return PlainSourceFileSystem(inner)

        return EncryptedFileSystem(inner, source, sealedFiles, storage.index)
    }
}
