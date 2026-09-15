package com.fserver.core.lifecycle.sync

import com.fserver.core.di.BackgroundScope
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.network.info.model.NetworkInfo
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.runner.SyncRunner
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Turns "a paired device just showed up" into a sync pass over that device's sources.
 *
 * Starts no scan of its own: it reads whoever discovery - started elsewhere - already found, plus
 * every peer that dialed in, so it costs nothing while nothing is looking.
 *
 * No timers and no retries: a pass that failed is not repeated until something actually changed.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class AutoSyncCoordinator(
    private val devicesRepository: DevicesRepository,
    private val networkInfoRepository: NetworkInfoRepository,
    private val storage: FServerStorage,
    private val syncRunner: SyncRunner,
    private val backgroundScope: BackgroundScope,
) {
    private val isWatching = AtomicBoolean(false)

    private var watcherJob: Job? = null

    /** Device ids from the previous emission, so an arrival can be told from a device still there. */
    private var visible: Set<String> = emptySet()

    /** The network the devices in [visible] were seen on. */
    private var seenOn: NetworkInfo? = null

    /** Idempotent: repeated calls keep the one watcher already running. */
    fun start(): Job? {
        if (!isWatching.compareAndSet(false, true)) return null

        val job = backgroundScope.launch {
            combine(
                devicesRepository.devices.all,
                networkInfoRepository.networkInfo,
            ) { devices, network ->
                Sighting(
                    deviceIds = devices.mapTo(mutableSetOf()) { it.deviceId },
                    network = network,
                )
            }.collect(::onSighting)
        }

        watcherJob = job

        job.invokeOnCompletion {
            watcherJob = null
            visible = emptySet()
            seenOn = null
            isWatching.store(false)
        }

        return job
    }

    /** Stops watching. [start] works again afterward, and re-syncs whatever is visible then. */
    suspend fun stop() {
        watcherJob?.cancelAndJoin()
    }

    private suspend fun onSighting(sighting: Sighting) {
        // Everything reachable over the old network is a different route now, so nothing carries
        // over and whatever is still on the list counts as newly arrived.
        if (sighting.network != seenOn) {
            visible = emptySet()
            seenOn = sighting.network
        }

        val appeared = sighting.deviceIds - visible
        visible = sighting.deviceIds

        if (appeared.isEmpty()) return

        // Read per arrival rather than held: sources are registered and dropped while we watch.
        val paired = storage.sources.all()
            .filter { it.status == SourceEntry.Status.Active && it.deviceId in appeared }
            .map(SourceEntry::deviceId)
            .distinct()

        for (deviceId in paired) {
            Timber.i("Device $deviceId appeared with sources registered against it, syncing them")
            syncRunner.runForDeviceAsync(deviceId)
        }
    }

    /** Who was visible at one moment, and over which network. */
    private data class Sighting(
        val deviceIds: Set<String>,
        val network: NetworkInfo?,
    )
}
