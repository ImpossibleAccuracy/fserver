package com.fserver.core.sync.transfer

import com.fserver.common.exception.TransferException
import com.fserver.common.model.ContentHash
import com.fserver.core.files.scan.toFiles
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.FileServerMessages.Upload
import com.fserver.core.network.dictionary.dto.ContentHashDto
import com.fserver.core.network.dictionary.dto.UploadKey
import com.fserver.core.network.dictionary.dto.toDto
import com.fserver.core.sync.index.LocalIndexWriter
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.remote.PeerIndexFetcher
import com.fserver.files.FilesNode
import com.fserver.files.upload.FileRecord
import com.fserver.files.upload.FileVersion
import com.fserver.net.session.PeerSession

/**
 * Pushes a file of a source through [FilePusher]: for a pass pushing it, and for the server handing
 * it back to the peer that asked.
 */
internal class SourceUploader(
    private val indexWriter: LocalIndexWriter,
    private val remoteIndex: PeerIndexFetcher,
    private val node: FilesNode,
    private val pusher: FilePusher,
) {
    /**
     * Pushes a file of [source], and records what the push learned: its hash, and that the peer
     * now holds it.
     *
     * @return hash of the bytes sent
     */
    suspend fun uploadFile(
        file: FileRecord,
        version: FileVersion? = file.metadata.version,
        source: SourceEntry,
        session: PeerSession<FileServerMessages>,
    ): ContentHash {
        val locator = file.locator
            ?: error("Cannot upload file ${file.id} because it has no locator")

        val opened = node.openSource(source.location.toFiles()).openFile(locator)
            ?: throw TransferException.FileNotFoundException("File ${file.id} is gone from $locator")

        val init = Upload.Init(
            key = UploadKey.Source(sourceId = source.id, fileId = file.id.value),
            file = file.toDto(sourceId = source.id, version = version),
        )

        val hash = pusher.push(
            session = session,
            init = init,
            file = opened,
            path = file.path,
            size = file.metadata.size,
            knownHash = file.content,
        ) ?: error("A source's Init is never answered with the file already there")

        if (file.content == null) {
            indexWriter.recordHash(source, file, hash)
        }

        remoteIndex.recordSent(
            source = source,
            file = init.file!!.copy(
                content = ContentHashDto(
                    value = hash.value,
                    algorithm = hash.algorithm
                )
            ),
        )

        return hash
    }
}
