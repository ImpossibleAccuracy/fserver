package com.fserver.core.sync.remote

import com.fserver.core.network.NetworkController
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.dictionary.dto.toDto
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.model.SourceEntry
import timber.log.Timber

/**
 * Tells a source's peer what this device ended its pass holding, so the peer's copy of our index is
 * current without it having to ask.
 *
 * Fire and forget both ways round: nothing is waited on and nothing is retried. The peer's copy is a
 * cache, so a push that never leaves - no session, a dropped link - costs it one `FetchFiles` on its
 * next pass. That is also why this never dials: a pass just finished talking to the peer, and waking
 * a radio for a message nobody is waiting for is not worth it.
 */
internal class IndexPublisher(
    private val storage: FServerStorage,
    private val networkController: NetworkController,
) {
    suspend fun publish(source: SourceEntry) {
        val session = networkController.incomingConnections.session(source.deviceId)
        if (session == null) {
            Timber.d("Not publishing index for source ${source.id}: no session with ${source.deviceId}")
            return
        }

        // Whole set, empty included: "this source holds nothing now" is news the peer needs.
        val files = storage.index.processedFiles(source.id)

        session.send(FileServerMessages.PublishIndex(source.id, files.map { it.toDto() }))
            .onFailure { Timber.w(it, "Could not publish index for source ${source.id}") }
    }
}
