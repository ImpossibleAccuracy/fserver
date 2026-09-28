package com.fserver.core.network.dictionary

import com.fserver.core.network.dictionary.dto.ContentHashDto
import com.fserver.core.network.dictionary.dto.FileRecordDto
import com.fserver.core.network.dictionary.dto.VersionDto
import com.fserver.core.sync.index.IndexedFileKey
import kotlinx.serialization.Serializable

/**
 * What one peer asks another to do. Travels inside [FileServerMessages.OperationWithConfirmation.Request],
 * which pairs it with the id the peer's [FileServerMessages.OperationWithConfirmation.Completed] is matched by.
 */
@Serializable
internal sealed interface RemoteOperation {
    /** Acts on a file the peer already knows from its own index. */
    @Serializable
    sealed interface File : RemoteOperation {
        val key: IndexedFileKey

        @Serializable
        data class Hash(override val key: IndexedFileKey) : File

        /** Delete the file, recording the deletion as [version] - or as the peer's own new one when null. */
        @Serializable
        data class Delete(
            override val key: IndexedFileKey,
            val version: VersionDto? = null,
        ) : File

        /**
         * Record [version] for content both sides already hold: [expected] bytes, or a deletion when
         * null. Refused if the file changed since.
         */
        @Serializable
        data class AdoptVersion(
            override val key: IndexedFileKey,
            val version: VersionDto,
            val expected: ContentHashDto?,
        ) : File

        /**
         * Rename the file - still holding [expected] - to [target]'s path, recording [target] as it
         * says and the old file's deletion as [deletedVersion], or as the peer's own new one when null.
         */
        @Serializable
        data class Move(
            override val key: IndexedFileKey,
            val expected: ContentHashDto,
            val target: FileRecordDto,
            val deletedVersion: VersionDto? = null,
        ) : File

        @Serializable
        data class Download(
            override val key: IndexedFileKey,
            val version: VersionDto? = null,
        ) : File
    }
}
