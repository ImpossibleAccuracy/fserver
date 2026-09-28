package com.fserver.core.sync.remote

import com.fserver.common.model.ContentHash
import com.fserver.core.network.dictionary.RemoteOperation
import com.fserver.core.network.dictionary.dto.ContentHashDto
import com.fserver.core.network.dictionary.dto.toDto
import com.fserver.core.network.utils.runRemoteOperation
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.model.SourceEntry
import com.fserver.files.upload.FileRecord
import com.fserver.files.upload.FileVersion

/**
 * The asking side of [RemoteOperation.File]: what a pass asks the peer to do to its copy of a file.
 * `FileOperationHandler` answers them there. Each returns once the peer confirmed.
 */
internal class PeerFileOperations(
    private val connector: PeerConnector,
) {
    suspend fun hash(source: SourceEntry, fileId: String) = run(
        source,
        RemoteOperation.File.Hash(key(source, fileId)),
    )

    /** [version] null lets the peer record the deletion as a new version of its own. */
    suspend fun delete(source: SourceEntry, fileId: String, version: FileVersion?) = run(
        source,
        RemoteOperation.File.Delete(key = key(source, fileId), version = version?.toDto()),
    )

    /** [expected] null means the peer's side is a deletion. */
    suspend fun adoptVersion(source: SourceEntry, fileId: String, version: FileVersion, expected: ContentHash?) = run(
        source,
        RemoteOperation.File.AdoptVersion(
            key = key(source, fileId),
            version = version.toDto(),
            expected = expected?.toDto(),
        ),
    )

    suspend fun move(
        source: SourceEntry,
        from: FileRecord,
        to: FileRecord,
        version: FileVersion?,
        deletedVersion: FileVersion?,
    ) = run(
        source,
        RemoteOperation.File.Move(
            key = key(source, from.id.value),
            expected = checkNotNull(from.content) { "Cannot move ${from.path}: content unknown" }.toDto(),
            target = to.toDto(source.id, version),
            deletedVersion = deletedVersion?.toDto(),
        ),
    )

    private suspend fun run(source: SourceEntry, operation: RemoteOperation) =
        connector.connectToDevice(source).runRemoteOperation(operation = operation)

    private fun key(source: SourceEntry, fileId: String) = IndexedFileKey(fileId = fileId, sourceId = source.id)
}

private fun ContentHash.toDto() = ContentHashDto(value = value, algorithm = algorithm)
