package com.fserver.core.sync.runner.pass

import com.fserver.core.di.BackgroundScope
import com.fserver.core.files.gc.GarbageCollector
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.remote.IndexPublisher
import com.fserver.core.util.TimeProvider
import kotlinx.coroutines.launch

/** What follows a pass, whichever device drove it. */
internal class PassCompletion(
    private val storage: FServerStorage,
    private val indexPublisher: IndexPublisher,
    private val garbageCollector: GarbageCollector,
    private val backgroundScope: BackgroundScope,
    private val timeProvider: TimeProvider,
) {
    /** A pass this device drove went through. */
    suspend fun localPassSucceeded(source: SourceEntry) {
        storage.sources.markSynced(source.id, timeProvider.now())
    }

    /** The lease on [source] is back. Pushed off the pass: nobody answers the message. */
    fun localPassEnded(source: SourceEntry) {
        backgroundScope.launch { indexPublisher.publish(source) }
    }

    /** The peer handed its lease on [sourceId] back. */
    suspend fun peerPassEnded(sourceId: String, succeeded: Boolean) {
        if (succeeded) storage.sources.markSynced(sourceId, timeProvider.now())
        garbageCollector.collectGarbageAsync()
    }

    /** A round of local passes is over. */
    fun localPassesEnded() {
        garbageCollector.collectGarbageAsync()
    }
}
