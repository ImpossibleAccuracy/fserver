package com.fserver.core.network.presence

import com.fserver.core.di.BackgroundScope
import com.fserver.core.network.TransportKind
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.network.info.model.NetworkInfo
import com.fserver.core.network.presence.PresenceController.Companion.RecheckInterval
import com.fserver.core.requirement.RequirementsChecker
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.time.Duration.Companion.seconds

/**
 * Decides when this device is on the air and when it is looking, for the whole process.
 *
 * One owner on purpose: both outlive any one screen, and whoever turns a radio on in passing does
 * it behind everyone else's back. The default policy, not the only one - a host that wants to drive
 * [DevicesRepository.advertising] and [DevicesRepository.discovery] itself simply never starts this.
 *
 * Nobody sets the state directly. Every caller that wants something running takes a [Handover] and
 * says what *it* wants; what runs is the union of every open handover, so a screen scanning for the
 * user cannot switch off what a background policy asked for, and closing a handover withdraws only
 * its own ask. Callers read what is actually running off
 * [DeviceDiscovery.runningMethods][com.fserver.core.network.device.DeviceDiscovery.runningMethods]
 * and its advertising twin, never off their own bookkeeping.
 *
 * Reconciles rather than fires once: a method that gave up is started again, one that lost what it
 * needs is taken down, and what is running is read back off the engine rather than remembered here.
 */
@OptIn(ExperimentalAtomicApi::class)
class PresenceController internal constructor(
    private val devicesRepository: DevicesRepository,
    private val requirementsChecker: RequirementsChecker,
    private val networkInfoRepository: NetworkInfoRepository,
    private val backgroundScope: BackgroundScope,
) {
    private val isRunning = AtomicBoolean(false)

    /** Guards [votes] and the two aggregates published from it. */
    private val lock = Any()

    private val votes = LinkedHashMap<Handover, Vote>()

    private val advertising = MutableStateFlow(false)
    private val discovery = MutableStateFlow<Set<TransportKind>>(emptySet())

    private val rechecks = MutableStateFlow(0)

    private var reconcileJob: Job? = null

    /** The network the methods currently on the air were started on. */
    private var advertisedOn: NetworkInfo? = null

    /** The network the scans currently running were started on. */
    private var scannedOn: NetworkInfo? = null

    /** One job per scan this controller started, so a single method can be stopped on its own. */
    private val scans = ConcurrentHashMap<TransportKind, Job>()

    /** Whether any open handover asks for this device to be findable. */
    val advertisingEnabled: StateFlow<Boolean> = advertising.asStateFlow()

    /** Every method any open handover asks to scan with. */
    val discoveryMethods: StateFlow<Set<TransportKind>> = discovery.asStateFlow()

    /**
     * When to look at the requirements again.
     *
     * Android announces none of what a radio depends on: a permission is granted in system
     * settings, a radio is switched off from the shade, and nothing calls back. Rather than a
     * receiver per source, this re-reads them - whenever [recheck] is called, and otherwise slowly.
     * The check is cheap and being late by [RecheckInterval] costs a device nothing.
     */
    private val recheckRequirements: Flow<Any> = merge<Any>(
        rechecks,
        flow {
            while (true) {
                emit(Unit)
                delay(RecheckInterval)
            }
        },
    )

    /**
     * Starts reconciling. Idempotent: repeated calls keep the one reconciler already running.
     *
     * Nothing comes up on its own afterward - a started controller with no open handover runs
     * nothing at all.
     *
     * @return `null` if already running, a `Job` that completes when it is stopped otherwise.
     */
    fun start(): Job? {
        if (!isRunning.compareAndSet(false, true)) return null

        val job = backgroundScope.launch {
            combine(
                advertising,
                discovery,
                devicesRepository.advertising.runningMethods,
                networkInfoRepository.networkInfo,
                recheckRequirements,
            ) { advertise, scan, onAir, network, _ ->
                Presence(
                    advertise = advertise,
                    scan = scan,
                    onAir = onAir,
                    network = network,
                )
            }.collect(::reconcile)
        }

        reconcileJob = job

        job.invokeOnCompletion {
            reconcileJob = null
            isRunning.store(false)
        }

        return job
    }

    /**
     * Takes this device off the air, stops what it started scanning, and stops reconciling.
     *
     * Handovers are left alone: they are their holders' to close, and [start] picks up whatever
     * they still ask for.
     */
    suspend fun stop() {
        reconcileJob?.cancelAndJoin()

        stopScans()
        devicesRepository.advertising.stopAll()

        advertisedOn = null
        scannedOn = null
    }

    /**
     * Look again now: something outside this process - a permission, a radio - may have changed.
     *
     * Cheaper and more prompt than waiting out [RecheckInterval], and the caller knows when it is
     * worth asking: coming back to the foreground is when a trip to system settings ends.
     */
    fun recheck() {
        rechecks.update { it + 1 }
    }

    /**
     * One caller's say in what runs, open until it is closed.
     *
     * Closing withdraws everything this handover asked for and nothing anyone else did; a closed
     * one ignores further calls rather than reviving its ask.
     */
    fun handover(): Handover = HandoverImpl().also { handover ->
        synchronized(lock) {
            votes[handover] = Vote()
            publish()
        }
    }

    /** What one caller wants running. Every setter is safe to call from any thread. */
    interface Handover : AutoCloseable {
        /** Whether this caller wants the device findable over every method that can announce. */
        // TODO: extend to accept list of methods
        fun setAdvertising(enabled: Boolean)

        /** The exact set this caller wants scanned. Replaces whatever it asked for before. */
        fun setDiscovery(methods: Set<TransportKind>)

        /** [methods] with one entry added or removed, for a caller driving one method at a time. */
        fun setDiscovery(method: TransportKind, enabled: Boolean)
    }

    private suspend fun reconcile(state: Presence) {
        reconcileAdvertising(state)
        reconcileDiscovery(state)
    }

    /**
     * Brings what is on the air in line with what should be: ready methods go up, methods that
     * lost what they needed come down.
     *
     * A method still missing a permission is skipped rather than attempted: this is not the place
     * to ask for one - a screen with something on it to explain the ask is - and the check is the
     * same one that screen runs, because an advertiser drives the same radios its scan listens on.
     */
    private suspend fun reconcileAdvertising(state: Presence) {
        if (!state.advertise) {
            if (state.onAir.isNotEmpty()) devicesRepository.advertising.stopAll()
            advertisedOn = null
            return
        }

        // An advertisement made on the previous network describes an address nobody on this one
        // can reach, so it comes down whole; the emptied set brings this straight back to start it
        // again.
        if (state.onAir.isNotEmpty() && state.network != advertisedOn) {
            devicesRepository.advertising.stopAll()
            return
        }

        TransportKind.entries.filterIsInstance<TransportKind.Automatic>().forEach { method ->
            val isReady = requirementsChecker.forTransport(method).isSatisfied

            when {
                isReady && method !in state.onAir ->
                    devicesRepository.advertising.start(method)
                        .onFailure { Timber.d(it, "not advertising over $method") }

                !isReady && method in state.onAir -> devicesRepository.advertising.stop(method)
            }
        }

        advertisedOn = state.network
    }

    /** The same for scanning, over exactly the methods the open handovers add up to. */
    private suspend fun reconcileDiscovery(state: Presence) {
        // A scan started on the previous network is looking at a subnet this device has left.
        if (scans.isNotEmpty() && state.network != scannedOn) {
            stopScans()
            return
        }

        scans.keys.filterNot(state.scan::contains).forEach(::stopScan)

        state.scan.forEach { method ->
            val isReady = requirementsChecker.forTransport(method).isSatisfied
            val isOurs = scans[method]?.isActive == true

            when {
                isReady && !isOurs -> startScan(method)
                !isReady && isOurs -> stopScan(method)
            }
        }

        scannedOn = state.network
    }

    /**
     * Cancelling the job is the stop: `DeviceDiscovery.start` runs until the scan ends, and clears
     * the method on its way out.
     */
    private fun startScan(method: TransportKind) {
        val job = backgroundScope.launch {
            devicesRepository.discovery.start(method)
                .onFailure { Timber.d(it, "not scanning over %s", method) }
        }

        scans[method] = job
        job.invokeOnCompletion { scans.remove(method, job) }
    }

    private fun stopScan(method: TransportKind) {
        scans.remove(method)?.cancel()
    }

    private fun stopScans() {
        scans.keys.toList().forEach(::stopScan)
    }

    /** Caller holds [lock]. */
    private fun publish() {
        advertising.value = votes.values.any { it.advertise }
        discovery.value = votes.values.flatMapTo(mutableSetOf()) { it.discovery }
    }

    private inner class HandoverImpl : Handover {
        private val closed = AtomicBoolean(false)

        override fun setAdvertising(enabled: Boolean) = edit { it.copy(advertise = enabled) }

        override fun setDiscovery(methods: Set<TransportKind>) =
            edit { it.copy(discovery = methods.toSet()) }

        override fun setDiscovery(method: TransportKind, enabled: Boolean) = edit {
            it.copy(
                discovery = if (enabled) it.discovery + method else it.discovery - method,
            )
        }

        override fun close() {
            if (!closed.compareAndSet(false, true)) return

            synchronized(lock) {
                votes.remove(this)
                publish()
            }
        }

        private fun edit(change: (Vote) -> Vote) {
            synchronized(lock) {
                val current = votes[this] ?: return
                votes[this] = change(current)
                publish()
            }
        }
    }

    /** What one handover asks for. */
    private data class Vote(
        val advertise: Boolean = false,
        val discovery: Set<TransportKind> = emptySet(),
    )

    /** Everything a reconcile is decided from, in one place. */
    private data class Presence(
        val advertise: Boolean,
        val scan: Set<TransportKind>,
        val onAir: Set<TransportKind.Automatic>,
        val network: NetworkInfo?,
    )

    companion object {
        private val RecheckInterval = 30.seconds

        /**
         * What a background policy should ask for: multicast DNS costs no permission and no
         * battery worth the name, while the rest are loud, slow, or run radios that drain a device
         * flat, and are worth scanning with only while a user is looking at what they turn up.
         */
        val BackgroundMethods: Set<TransportKind> = setOf(TransportKind.MulticastDns)
    }
}
