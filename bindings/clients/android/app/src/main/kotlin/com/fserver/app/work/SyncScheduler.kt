package com.fserver.app.work

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.fserver.core.storage.SyncPreferencesRepository
import com.fserver.core.sync.model.SyncPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.hours
import kotlin.time.toJavaDuration

/**
 * Keeps [SyncWorker] registered, under the constraints the user set.
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
    private val syncPreferences: SyncPreferencesRepository,
) {
    private val workManager = WorkManager.getInstance(context.applicationContext)

    /** Follows the settings for as long as [scope] lives. */
    fun start(scope: CoroutineScope) {
        scope.launch {
            syncPreferences.preferences
                .distinctUntilChanged()
                .collect(::enqueue)
        }
    }

    private fun enqueue(preferences: SyncPreferences) {
        Timber.i("Scheduling periodic sync every $Period under ${preferences.deviceConstraints}")

        val request = PeriodicWorkRequestBuilder<SyncWorker>(Period.toJavaDuration())
            .setConstraints(preferences.deviceConstraints.toWorkConstraints())
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
    private fun SyncPreferences.DeviceConstraints.toWorkConstraints(): Constraints =
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
