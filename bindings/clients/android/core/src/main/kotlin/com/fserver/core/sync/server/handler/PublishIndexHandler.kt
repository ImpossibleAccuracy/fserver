package com.fserver.core.sync.server.handler

import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.dto.latestHlc
import com.fserver.core.network.dictionary.dto.toRemoteIndexed
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.server.SourceAuthorizer
import com.fserver.core.sync.version.HybridLogicalClock
import com.fserver.core.util.TimeProvider
import com.fserver.net.session.PeerSession
import timber.log.Timber

/**
 * Takes `PublishIndex`: what the peer ended its pass holding, recorded against the source it named.
 *
 * Nothing is answered - the sender is not waiting. The id still goes through [SourceAuthorizer]
 * like every other inbound one: without that check any authenticated device could overwrite this
 * one's record of any source.
 */
internal class PublishIndexHandler(
    private val authorizer: SourceAuthorizer,
    private val storage: FServerStorage,
    private val timeProvider: TimeProvider,
    private val clock: HybridLogicalClock,
) {
    suspend fun handle(
        message: FileServerMessages.PublishIndex,
        session: PeerSession<FileServerMessages>,
    ) {
        val source = runCatchingCancellable {
            authorizer.authorizedSource(session.identity, message.sourceId)
        }.getOrElse { t ->
            Timber.w(t, "Ignoring published index for ${message.sourceId} from ${session.identity.deviceId}")
            return
        }

        val seenAt = timeProvider.now()

        storage.remoteIndex.replace(
            sourceId = source.id,
            deviceId = source.deviceId,
            files = message.files.map { it.toRemoteIndexed(seenAt) },
        )

        message.files.latestHlc()?.let { clock.receive(it) }

        Timber.d("Recorded ${message.files.size} remote files for source ${source.id}")
    }
}
