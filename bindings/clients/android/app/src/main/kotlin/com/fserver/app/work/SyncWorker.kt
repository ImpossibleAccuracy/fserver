package com.fserver.app.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.fserver.core.sync.SourcesController
import kotlinx.coroutines.CancellationException
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject
import timber.log.Timber

/**
 * One sync pass over every registered source, run with no screen open.
 *
 * `:core` keeps no timer of its own - a host schedules its own passes - so this and
 * [SyncScheduler] are the whole of "sync happens without a tap". Which sources move, and whether
 * the device's own constraints allow any of them to, is still the engine's call.
 *
 * Reaches the container through the global Koin context rather than a `WorkerFactory`, because
 * WorkManager constructs workers itself and a factory would only be a second route to the graph
 * the process already has.
 */
class SyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params), KoinComponent {

    private val sources: SourcesController by inject()

    override suspend fun doWork(): Result {
        Timber.i("Scheduled sync pass starting")

        return try {
            sources.runSync()
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Timber.w(e, "Scheduled sync pass failed")

            // A pass that broke is nearly always a peer that was not there this time, so the next
            // window is as good a moment as any. Per-source failures never reach here - the engine
            // records those on the pass itself.
            Result.retry()
        }
    }
}
