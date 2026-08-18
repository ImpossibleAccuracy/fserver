package com.fserver.app.domain

import com.fserver.app.data.AppSettingsStore
import com.fserver.app.domain.AdvertisementLifecycleHandler.Companion.RECHECK_INTERVAL
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.TransportKind
import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.network.info.model.NetworkInfo
import com.fserver.core.requirement.RequirementsChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.time.Duration.Companion.seconds

/**
 * Decides when this device is on the air, for the whole app.
 *
 * One owner on purpose: advertising outlives any one screen, and a screen that turned the device
 * findable on its way past would be doing it behind the "discoverable" setting's back.
 *
 * Reconciles rather than fires once — an advertiser that gave up is started again, one that lost
 * what it needed is taken down, and what is on the air is read back from `:core` rather than
 * remembered here.
 */
class AdvertisementLifecycleHandler(
    private val devicesRepository: DevicesRepository,
    private val requirementsChecker: RequirementsChecker,
    private val networkInfoRepository: NetworkInfoRepository,
    private val appSettings: AppSettingsStore,
) {
    private val rechecks = MutableStateFlow(0)

    /** The network the advertisers currently on the air were started on. */
    private var advertisedOn: NetworkInfo? = null

    /**
     * When to look at the requirements again.
     *
     * Android announces none of what advertising depends on: a permission is granted in system
     * settings, a radio is switched off from the shade, and nothing calls back. Rather than a
     * receiver per source, this re-reads them - whenever the host asks, and otherwise slowly. The
     * check is cheap and being late by [RECHECK_INTERVAL] costs a device nothing.
     */
    private val recheckRequirements: Flow<Any> = merge<Any>(
        rechecks,
        flow {
            while (true) {
                emit(Unit)
                delay(RECHECK_INTERVAL)
            }
        },
    )

    /**
     * Starts reconciling, and keeps at it until [scope] dies. [isAppVisible] is the host's half of
     * the decision: the setting says whether this device may be findable, the host says whether
     * there is anyone in front of it.
     */
    fun start(scope: CoroutineScope, isAppVisible: Flow<Boolean>): Job = scope.launch {
        combine(
            isAppVisible,
            appSettings.discoverable,
            devicesRepository.advertisingMethods,
            networkInfoRepository.networkInfo,
            recheckRequirements,
        ) { visible, discoverable, onAir, network, _ ->
            Advertisement(
                wanted = visible && discoverable,
                onAir = onAir,
                network = network,
            )
        }.collect(::reconcile)
    }

    /**
     * Look again now: something outside the app - a permission, a radio - may have changed.
     *
     * Cheaper and more prompt than waiting out [RECHECK_INTERVAL], and the host knows when it is
     * worth asking: coming back to the foreground is when a trip to system settings ends.
     */
    fun recheck() {
        rechecks.update { it + 1 }
    }

    /** Takes this device off the air, whatever the setting says. */
    suspend fun stop() = devicesRepository.stopAdvertising()

    /**
     * Brings what is on the air in line with what should be: ready methods go up, methods that
     * lost what they needed come down.
     *
     * A method still missing a permission is skipped rather than attempted: this is not the place
     * to ask for one - the discovery screen is, where there is something on screen to explain it -
     * and the check is the same one that screen runs, because an advertiser drives the same radios
     * its scan listens on.
     */
    private suspend fun reconcile(state: Advertisement) {
        if (!state.wanted) {
            if (state.onAir.isNotEmpty()) devicesRepository.stopAdvertising()
            advertisedOn = null
            return
        }

        // An advertisement made on the previous network describes an address nobody on this one
        // can reach, so it comes down whole; the emptied set brings this straight back to start it
        // again.
        if (state.onAir.isNotEmpty() && state.network != advertisedOn) {
            devicesRepository.stopAdvertising()
            return
        }

        TransportKind.entries.filterIsInstance<TransportKind.Automatic>().forEach { method ->
            val isReady = requirementsChecker.forTransport(method).isSatisfied

            when {
                isReady && method !in state.onAir ->
                    devicesRepository.startAdvertising(method)
                        .onFailure { Timber.d(it, "not advertising over $method") }

                !isReady && method in state.onAir -> devicesRepository.stopAdvertising(method)
            }
        }

        advertisedOn = state.network
    }

    /** Everything the decision to advertise is made from, in one place. */
    private data class Advertisement(
        val wanted: Boolean,
        val onAir: Set<TransportKind.Automatic>,
        val network: NetworkInfo?,
    )

    private companion object {
        val RECHECK_INTERVAL = 30.seconds
    }
}
