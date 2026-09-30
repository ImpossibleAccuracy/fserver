package com.fserver.core.sync.server.handler.upload

import com.fserver.common.model.ContentHash
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.dto.UploadKey
import com.fserver.files.fs.FsFile
import com.fserver.net.security.identity.PeerIdentity

/**
 * What one kind of [UploadKey] means to the receiver: who may send it, and where it lands. The
 * upload itself - staging, chunks, hash, resume - is the same for every kind; see [FileUploadHandler].
 */
internal interface UploadTarget {
    /**
     * Authorizes [init] from [peer] and opens its staging, or answers without opening: the file
     * is over limits, already there, or no longer wanted.
     */
    suspend fun open(
        peer: PeerIdentity,
        init: FileServerMessages.Upload.Init,
        uploads: SessionUploads
    ): Opening

    /** The sender gave the file up. */
    suspend fun abandon(peer: PeerIdentity, key: UploadKey, reason: String)

    sealed interface Opening {
        class Staged(val landing: UploadLanding, val staging: FsFile, val committed: Long) : Opening

        class Answered(val answer: FileServerMessages.Upload) : Opening
    }
}

/** Where one open upload lands and what records it. Made by its [UploadTarget] at [FileServerMessages.Upload.Init]. */
internal interface UploadLanding {
    /** What the sender declared: no chunk may reach past it. */
    val size: Long

    /** For progress and logs. */
    val path: String

    /**
     * Throws when [peer] may not go on with this upload: whatever the authorization throws, or
     * [com.fserver.common.exception.TransferException.UploadStoppedException] once what the upload
     * belongs to takes nothing more.
     */
    suspend fun ensureOpen(peer: PeerIdentity)

    /** Records that `[0, offset)` of the staged file is flushed and survives a crash. */
    suspend fun checkpoint(offset: Long)

    /** The staged file is whole and matches [hash]: puts it in place and records it. */
    suspend fun place(staged: FsFile, hash: ContentHash)

    /** The staged bytes arrived corrupted: nothing is worth resuming. */
    suspend fun discard(staged: FsFile)
}
