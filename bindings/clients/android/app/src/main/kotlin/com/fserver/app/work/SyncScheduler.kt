package com.fserver.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.fserver.core.storage.RegisteredSourcesRepository
import com.fserver.core.sync.model.SourceEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

/**
 * Keeps [SyncWorker] registered, under the loosest constraints any source asks for: one worker
 * serves every source, so it must wake whenever at least one of them may run.
 *
 * The constraints are mirrored rather than left to the engine alone: the engine's own
 * `DeviceConstraintChecker` still has the last word, but a pass that WorkManager never starts
 * costs no wakeup at all, and a pass started only to be refused costs one.
 *
 * Re-enqueued with `UPDATE` when the settings change, not cancelled and re-created: `UPDATE` keeps
 * the period where it is, so flipping "only on Wi-Fi" does not push the next pass a full period
 * away.
 */
class SyncScheduler(
    context: Context,
    private val sources: RegisteredSourcesRepository,
) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    /** Follows the settings for as long as [scope] lives. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            sources.sources
                .map(::loosestConstraints)
                .distinctUntilChanged()
                .collect(::enqueue)
        }
    }

    private fun loosestConstraints(sources: List<SourceEntry>): SourceEntry.Preferences.DeviceConstraints {
        val constraints = sources
            .filter { it.status !is SourceEntry.Status.Disabled }
            .map { it.preferences.deviceConstraints }
            .ifEmpty { return SourceEntry.Preferences.Default.deviceConstraints }

        return SourceEntry.Preferences.DeviceConstraints(
            wifiRequired = constraints.all { it.wifiRequired },
            chargingRequired = constraints.all { it.chargingRequired },
        )
    }

    private fun enqueue(constraints: SourceEntry.Preferences.DeviceConstraints) {
        Timber.i("Scheduling periodic sync every $Period under $constraints")

        val request = PeriodicWorkRequestBuilder<SyncWorker>(Period.toJavaDuration())
            .setConstraints(constraints.toWorkConstraints())
            .build()

        workManager.enqueueUniquePeriodicWork(
            UniqueWorkName,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    /**
     * `UNMETERED` rather than a Wi-Fi check: what the setting is protecting is the user's data
     * allowance, and that is what Android can actually test for.
     */
    private fun SourceEntry.Preferences.DeviceConstraints.toWorkConstraints(): Constraints =
        Constraints.Builder()
            .setRequiredNetworkType(
                if (wifiRequired) NetworkType.UNMETERED else NetworkType.CONNECTED
            )
            .setRequiresCharging(chargingRequired)
            .build()

    private companion object {
        /** Well above WorkManager's 15-minute floor: a pass is not cheap, and nothing waits on it. */
        val Period = 6.hours

        const val UniqueWorkName = "fserver-periodic-sync"
    }
}
