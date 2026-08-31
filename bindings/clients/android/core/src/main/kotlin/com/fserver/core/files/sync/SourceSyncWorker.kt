package com.fserver.core.files.sync

import com.fserver.core.di.BackgroundScope
import com.fserver.core.files.source.FileSource
import com.fserver.core.store.FileSourcesStore
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/**
 * Walks every registered source on a timer.
 *
 * A plain coroutine loop on the engine's background scope, deliberately: `:core` ships as a
 * standalone library, so it cannot assume WorkManager or edit the host's manifest. That caps what
 * this can promise - it lives and dies with the process. A host that needs passes to survive death
 * or doze schedules its own job and calls [runOnce].
 */
@OptIn(ExperimentalAtomicApi::class)
internal class SourceSyncWorker(
    private val store: FileSourcesStore,
    private val backgroundScope: BackgroundScope,
) {
    private val isRunning = AtomicBoolean(false)
    private var job: Job? = null

    /** Idempotent: a second call while running is ignored rather than starting a second loop. */
    fun start(interval: Duration = DEFAULT_INTERVAL) {
        if (!isRunning.compareAndSet(false, true)) return

        job = backgroundScope.launch {
            while (coroutineContext.isActive) {
                delay(interval)

                try {
                    runOnce()
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    // One bad pass must not kill the timer.
                    Timber.w(e, "Periodic source pass failed")
                }
            }
        }
    }

    suspend fun stop() {
        job?.cancelAndJoin()
        job = null
        isRunning.store(false)
    }

    /** One pass over every registered source. Safe to call directly from a host-owned job. */
    suspend fun runOnce() {
        for (source in store.all()) {
            process(source)
        }
    }

    private suspend fun process(source: FileSource) {
        val alreadyDone = store.processedFiles(source.id).associateBy { it.path }

        // TODO: rescan the source, and for each scanned file keep it only when `alreadyDone` has
        //  no record for its path or `ProcessedFile.matches` says it changed. Hand the survivors
        //  to the transfer engine, then `markProcessed` only what actually went through - marking
        //  before the handoff lands would silently skip the file forever.
        //  Blocked on the scanner reporting mtime: `ScannedFile` carries path/size only today, so
        //  `matches` has nothing to compare against.
        Timber.d(
            "Periodic pass over source ${source.id} (${source.accessModel}), " +
                "${alreadyDone.size} files already processed"
        )
    }

    private companion object {
        val DEFAULT_INTERVAL: Duration = 15.minutes
    }
}
