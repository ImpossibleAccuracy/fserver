package com.fserver.core.sync.remote

import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.di.BackgroundScope
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.sync.model.SourceEntry
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Asks the peer to run the pass over a source only it drives. Off the caller's pass, and nothing
 * waits for an answer: the dial alone may take long, and there is none.
 */
internal class PeerSyncRequester(
    private val peers: PeerConnector,
    private val backgroundScope: BackgroundScope,
) {
    fun requestAsync(source: SourceEntry): Job = backgroundScope.launch {
        runCatchingCancellable {
            peers.connectToDevice(source).send(FileServerMessages.RequestSync(source.id)).getOrThrow()
        }
            .onSuccess { Timber.i("Asked ${source.deviceId} to sync source ${source.id}") }
            .onFailure { Timber.w(it, "Could not ask ${source.deviceId} to sync source ${source.id}") }
    }
}
