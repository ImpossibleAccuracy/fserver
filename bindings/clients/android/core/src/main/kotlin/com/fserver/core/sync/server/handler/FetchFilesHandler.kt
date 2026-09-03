package com.fserver.core.sync.server.handler

import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.dto.toDto
import com.fserver.core.sync.index.LocalChangesIndexer
import com.fserver.core.sync.server.SourceAuthorizer
import com.fserver.net.session.PeerSession
import timber.log.Timber

/** Answers `FetchFiles`: what this device holds for one source, as the peer last left it. */
internal class FetchFilesHandler(
    private val authorizer: SourceAuthorizer,
    private val localIndexer: LocalChangesIndexer,
) {
    suspend fun handle(
        event: PeerSession.Inbound<FileServerMessages>,
        message: FileServerMessages.FetchFiles.Request,
        session: PeerSession<FileServerMessages>,
    ) {
        val reply = event.reply
        if (reply == null) {
            Timber.w("Cannot answer FetchFiles(${message.sourceId}) from ${session.identity.deviceId}: no reply channel")
            return
        }

        val files = runCatchingCancellable {
            val source = authorizer.authorizedSource(session.identity, message.sourceId)
            localIndexer.refresh(source)
        }

        files.fold(
            onSuccess = { indexed ->
                reply(FileServerMessages.FetchFiles.FilesList(indexed.map { it.toDto() }))
            },
            onFailure = { t ->
                Timber.w(
                    t,
                    "Cannot list source ${message.sourceId} for ${session.identity.deviceId}"
                )

                // Answered rather than dropped: otherwise the peer waits out its request timeout.
                reply(
                    FileServerMessages.FetchFiles.Failed(
                        sourceId = message.sourceId,
                        reason = t.message ?: "Unknown error",
                    )
                )
            },
        )
    }
}
