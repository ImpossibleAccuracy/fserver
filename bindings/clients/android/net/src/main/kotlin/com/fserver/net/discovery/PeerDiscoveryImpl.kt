package com.fserver.net.discovery

import com.fserver.net.NetworkException
import com.fserver.net.config.ConfigAware
import com.fserver.net.config.NetworkConfig
import com.fserver.net.config.NetworkConfigHolder
import com.fserver.net.security.auth.advertisableMethods
import com.fserver.net.spi.Advertiser
import com.fserver.net.spi.DiscoveryProvider
import com.fserver.net.spi.SpiId
import com.fserver.net.utils.netRunCatching
import com.fserver.net.wire.ProtocolVersions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

@OptIn(ExperimentalAtomicApi::class)
internal class PeerDiscoveryImpl(
    private val configHolder: NetworkConfigHolder<*>,
    private val scope: CoroutineScope,
) : PeerDiscovery, ConfigAware {
    private val config: NetworkConfig<*> get() = configHolder.current

    private val registry = PeerRegistry()

    override val peers: StateFlow<List<DiscoveredPeer>> = registry.peers
        .map { it.values.toList() }
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    private val running = MutableStateFlow<Set<SpiId>>(emptySet())
    override val activeScans: StateFlow<Set<SpiId>> = running.asStateFlow()

    private val scanJobs = ConcurrentHashMap<SpiId, Job>()

    private val advertisingLock = Mutex()
    private var advertisingJobs: List<Job> = emptyList()
    private val advertisementState = AtomicBoolean(false)

    override suspend fun scan(
        params: DiscoveryProvider.ScanParams
    ): Result<List<DiscoveredPeer>> = netRunCatching {
        val identity = config.identityStore.local()

        val provider = config.discoveryProviders.firstOrNull { it.accepts(params) }
            ?: throw NetworkException.Transport("no discovery provider handles $params")

        if (provider.id in running.value) return@netRunCatching emptyList()

        running.update(provider.id, add = true)
        val found = LinkedHashMap<String, DiscoveredPeer>()

        try {
            // Run scan in background so that we can cancel it if needed.
            coroutineScope {
                val job = launch {
                    provider.scan(params).collect { event ->
                        when (event) {
                            is DiscoveryProvider.Event.Appeared -> {
                                if (event.peer.attributes[PeerAttributes.DEVICE_ID] == identity.deviceId) {
                                    // Discovery provider found its own device, ignore it
                                    return@collect
                                }

                                val peer = registry.record(event.peer)
                                found[peer.advertised.deviceId] = peer
                            }

                            is DiscoveryProvider.Event.Disappeared ->
                                registry.forgetRoute(event.endpointAddress)

                            is DiscoveryProvider.Event.Failed -> config.logger.warn(
                                "discovery ${provider.id.value} failed",
                                event.cause
                            )
                        }
                    }
                }

                scanJobs[provider.id] = job
                job.join()
            }
        } finally {
            scanJobs.remove(provider.id)
            running.update(provider.id, add = false)
        }

        found.values.toList()
    }

    override fun stopScan(id: SpiId) {
        scanJobs.remove(id)?.cancel()
        running.update(id, add = false)
    }

    override suspend fun startAdvertising(): Result<Unit> = netRunCatching {
        advertisingLock.withLock {
            if (!advertisementState.compareAndSet(false, true)) return@withLock
            launchAdvertisers(config)
        }
    }

    override suspend fun stopAdvertising() {
        advertisingLock.withLock {
            if (!advertisementState.compareAndSet(true, false)) return@withLock
            haltAdvertisers()
        }
        config.logger.debug("advertising stopped")
    }

    /**
     * Reconciles what the previous config started. Scans on a provider that dropped out are
     * stopped, and the advertisers are restarted only if this device is on the air *and* what it
     * would say - or who would say it - actually changed. A reload is not a request to advertise:
     * a node the host deliberately kept silent stays silent.
     */
    override suspend fun onConfigChanged(old: NetworkConfig<*>, new: NetworkConfig<*>) {
        val installed = new.discoveryProviders.mapTo(mutableSetOf()) { it.id }
        scanJobs.keys.filterNot { it in installed }.forEach(::stopScan)

        advertisingLock.withLock {
            if (!advertisementState.load()) return@withLock

            val unchanged = old.advertisers == new.advertisers &&
                    old.policy.advertisement == new.policy.advertisement &&
                    advertisement(old) == advertisement(new)
            if (unchanged) return@withLock

            haltAdvertisers()
            launchAdvertisers(new)
        }
    }

    override fun peer(deviceId: String): Flow<DiscoveredPeer?> = registry.peers
        .map { it[deviceId] }
        .distinctUntilChanged()

    /** Caller holds [advertisingLock]. */
    private suspend fun launchAdvertisers(config: NetworkConfig<*>) {
        if (!config.policy.advertisement.enabled) {
            config.logger.debug("advertising is off; this device will not announce itself")
            return
        }

        // An advertiser that gave up leaves a finished job behind;
        // keeping it would make every later call a no-op and device would stay invisible.
        advertisingJobs = advertisingJobs.filter(Job::isActive)
        if (advertisingJobs.isNotEmpty()) return

        config.logger.debug("starting advertising with ${config.advertisers.joinToString { it.id.value }}")

        val payload = advertisement(config)
        advertisingJobs = config.advertisers
            .map { advertiser ->
                scope.launch {
                    try {
                        advertiser.advertise(payload).collect { event ->
                            if (event is Advertiser.Event.Failed) {
                                config.logger.warn(
                                    "advertiser ${advertiser.id.value} failed",
                                    event.cause
                                )
                            }
                        }
                    } catch (e: Exception) {
                        config.logger.error("advertiser ${advertiser.id.value} failed", e)
                    }
                }
            }
            .onEach { job ->
                // When an advertiser stops, check if any are still running
                job.invokeOnCompletion {
                    advertisingJobs = advertisingJobs.filter { it.isActive }
                    val isAnyActive = advertisingJobs.isNotEmpty()
                    if (!isAnyActive) {
                        config.logger.debug("All advertisers have stopped; this device is no longer discoverable")
                        advertisementState.store(false) // Clear lock, so new advertising can be started
                    }
                }
            }
    }

    /**
     * Waits for the advertisers to be gone rather than merely told to go.
     * Caller should hold [advertisingLock].
     */
    private suspend fun haltAdvertisers() {
        val running = advertisingJobs
        advertisingJobs = emptyList()
        running.forEach { it.cancelAndJoin() }
    }

    /** Compute payload for advertisers */
    private suspend fun advertisement(config: NetworkConfig<*>): Advertiser.Payload {
        val identity = config.identityStore.local()
        return Advertiser.Payload(
            identity = identity,
            essential = buildMap {
                put(PeerAttributes.DEVICE_ID, identity.deviceId)
                put(PeerAttributes.PROTOCOL_MIN, ProtocolVersions.SUPPORTED.first.toString())
                put(PeerAttributes.PROTOCOL_MAX, ProtocolVersions.SUPPORTED.last.toString())
                val authMethods = config.advertisableMethods()
                if (authMethods.isNotEmpty()) {
                    put(
                        PeerAttributes.AUTH_METHODS,
                        authMethods.joinToString(PeerAttributes.SEPARATOR) { it.value },
                    )
                }
                // Essential when published at all: telling one device from another in a list is
                // exactly what a name is for, and a transport short of room should not drop it
                // ahead of the decoration.
                if (config.policy.advertisement.publishName) put(
                    PeerAttributes.DISPLAY_NAME,
                    identity.displayName
                )
            },
            optional = buildMap {
                putAll(config.advertisedAttributes)
            },
        )
    }

    private fun MutableStateFlow<Set<SpiId>>.update(
        id: SpiId,
        add: Boolean
    ) {
        value = if (add) value + id else value - id
    }
}
