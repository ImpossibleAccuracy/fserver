package com.fserver.core.sync.server.handler

import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.sync.model.drivesSync
import com.fserver.core.sync.runner.SyncRunner
import com.fserver.core.sync.server.SourceAuthorizer
import com.fserver.net.session.PeerSession
import timber.log.Timber

/** Takes `RequestSync`: the peer asks for a pass over a source this device drives. Nothing is answered. */
internal class SyncRequestHandler(
    private val authorizer: SourceAuthorizer,
    private val syncRunner: SyncRunner,
) {
    suspend fun handle(
        message: FileServerMessages.RequestSync,
        session: PeerSession<FileServerMessages>,
    ) {
        val source = runCatchingCancellable {
            authorizer.authorizedSource(session.identity, message.sourceId)
        }.getOrElse { t ->
            Timber.w(t, "Ignoring sync request for ${message.sourceId} from ${session.identity.deviceId}")
            return
        }

        if (!source.drivesSync) {
            Timber.w("Ignoring sync request for ${source.id} from ${source.deviceId}: ${source.syncMode.type} runs from there")
            return
        }

        Timber.i("Device ${source.deviceId} asked to sync source ${source.id}")
        syncRunner.requestSourceAsync(source.id)
    }
}
