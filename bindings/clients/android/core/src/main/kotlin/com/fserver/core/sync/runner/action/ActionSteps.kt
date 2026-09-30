package com.fserver.core.sync.runner.action

import com.fserver.common.model.ContentHash
import com.fserver.core.network.dictionary.dto.toDto
import com.fserver.core.sync.fileops.FileDeleter
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.LocalIndexWriter
import com.fserver.core.sync.index.toIndexed
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.remote.PeerConnector
import com.fserver.core.sync.remote.PeerFileOperations
import com.fserver.core.sync.transfer.FileDownloader
import com.fserver.core.sync.transfer.SourceUploader
import com.fserver.files.upload.FileRecord
import com.fserver.files.upload.FileVersion

/**
 * Common/simple operations [FileActionRunner] wants to run.
 */
internal class ActionSteps(
    private val connector: PeerConnector,
    private val indexWriter: LocalIndexWriter,
    private val peerFiles: PeerFileOperations,
    private val sourceUploader: SourceUploader,
    private val fileDownloader: FileDownloader,
    private val fileDeleter: FileDeleter,
) {
    /** @return hash of the bytes sent */
    suspend fun upload(source: SourceEntry, file: FileRecord, version: FileVersion?): ContentHash =
        sourceUploader.uploadFile(
            file = file,
            version = version,
            source = source,
            session = connector.connectToDevice(source),
        )

    suspend fun download(source: SourceEntry, file: FileRecord, version: FileVersion?) =
        fileDownloader.download(
            source = source,
            key = key(source, file),
            sizeBytes = file.metadata.size,
            version = version?.toDto(),
        )

    /** The peer's deletion, not a new one of ours: recorded under its [version]. */
    suspend fun deleteLocal(source: SourceEntry, file: FileRecord, version: FileVersion?) {
        fileDeleter.delete(source, key(source, file), version?.toIndexed(), expected = file)
    }

    suspend fun deleteRemote(source: SourceEntry, file: FileRecord, version: FileVersion?) =
        peerFiles.delete(source, file.id.value, version)

    /** Records [version] for [file] here, unless it already has it. [expected] null means deleted. */
    suspend fun adoptLocally(source: SourceEntry, file: FileRecord, version: FileVersion?, expected: ContentHash?) {
        if (version == null || file.metadata.version == version) return

        indexWriter.adoptVersion(source, key(source, file), version.toIndexed(), expected)
    }

    /** Asks the peer to record [version] for [file], unless it already has it. */
    suspend fun adoptRemotely(source: SourceEntry, file: FileRecord, version: FileVersion?, expected: ContentHash?) {
        if (version == null || file.metadata.version == version) return

        peerFiles.adoptVersion(source, file.id.value, version, expected)
    }

    private fun key(source: SourceEntry, file: FileRecord) =
        IndexedFileKey(fileId = file.id.value, sourceId = source.id)
}
