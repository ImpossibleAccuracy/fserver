package com.fserver.core.sync.runner

import com.fserver.common.exception.SyncException
import com.fserver.common.utils.runCatchingCancellable
import com.fserver.core.di.BackgroundScope
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.runner.pass.PassCompletion
import com.fserver.core.sync.runner.pass.SourcePassExecutor
import com.fserver.core.sync.setup.SourceSetupExchange
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import timber.log.Timber

/**
 * Decides when passes run and over which sources, one batch at a time. What one source's pass
 * does is [SourcePassExecutor]'s.
 *
 * No timer of its own: host that wants periodic passes schedules its own job and calls `SourcesController.runSync`.
 */
internal class SyncRunner(
    private val storage: FServerStorage,
    private val passExecutor: SourcePassExecutor,
    private val completion: PassCompletion,
    private val sourceSetup: SourceSetupExchange,
    private val backgroundScope: BackgroundScope,
) {
    private val mutex = Mutex()

    /** One pass over every registered source. Waits for a pass already running. */
    suspend fun runOnce() = mutex.withLock { runPass(storage.sources.all()) }

    /**
     * One pass over [sourceId] alone. Waits for a pass already running. [force] skips the device
     * constraints - the user asked for this pass explicitly.
     */
    suspend fun runSource(sourceId: String, force: Boolean) = mutex.withLock {
        runPass(listOfNotNull(storage.sources.findById(sourceId)), force)
    }

    /**
     * One forced pass over [source], then [block] under the same lease - see [SourcePassExecutor.process].
     * Throws what the pass failed with, and [SyncException.SourceBusyException] when it could not run.
     */
    suspend fun <T> runSourceThen(source: SourceEntry, block: suspend (SourceEntry) -> T): T = mutex.withLock {
        var result: Result<T>? = null
        passExecutor.process(source, force = true) { result = Result.success(block(it)) }

        result?.getOrThrow() ?: throw SyncException.SourceBusyException("Source ${source.id} pass did not run")
    }

    /** [runSource] on the background scope. Waits for a pass already running rather than skipping. */
    fun runSourceAsync(sourceId: String): Job = backgroundScope.launch {
        runCatchingCancellable { runSource(sourceId, force = false) }
            .onFailure { Timber.w(it, "Source pass ($sourceId) failed") }
    }

    /**
     * One pass over every registered source, launched on the engine's background scope.
     * Skips if a pass is already running.
     */
    fun runOnceAsync(): Job = launchPass("one-off") { storage.sources.all() }

    /**
     * One pass over the sources paired with [deviceId] only, launched on the background scope.
     * Skips if a pass is already running - that pass covers this device too.
     */
    fun runForDeviceAsync(deviceId: String): Job = launchPass("device $deviceId") {
        storage.sources.all().filter { it.deviceId == deviceId }
    }

    private fun launchPass(label: String, select: suspend () -> List<SourceEntry>): Job =
        backgroundScope.launch {
            if (!mutex.tryLock()) {
                Timber.w("Source pass ($label) skipped: another pass is still running")
                return@launch
            }

            try {
                runPass(select())
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Timber.w(e, "Source pass ($label) failed")
            } finally {
                mutex.unlock()
            }
        }

    private suspend fun runPass(sources: List<SourceEntry>, force: Boolean = false) {
        for (source in sources) {
            try {
                passExecutor.process(source, force)

                Timber.i("Source ${source.id} pass completed successfully")
            } catch (e: Exception) {
                if (e is CancellationException) throw e

                Timber.e(e, "Source pass failed for ${source.id}")
            }
        }

        completion.localPassesEnded()
        backgroundScope.launch { sourceSetup.resendPending(sources) }
    }
}
