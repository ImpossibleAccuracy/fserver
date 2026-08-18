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
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

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
    private val scanJobs = ConcurrentHashMap<SpiId, Job>()
    override val activeScans: StateFlow<Set<SpiId>> = running.asStateFlow()

    private val advertisingLock = Mutex()
    private val advertisingJobs = ConcurrentHashMap<SpiId, Job>()
    private val advertising = MutableStateFlow<Set<SpiId>>(emptySet())
    override val activeAdvertisers: StateFlow<Set<SpiId>> = advertising.asStateFlow()

    override suspend fun scan(
        params: DiscoveryProvider.ScanParams
    ): Result<List<DiscoveredPeer>> = netRunCatching {
        val identity = config.identityStore.local()

        val provider = config.discoveryProviders.firstOrNull { it.accepts(params) }
            ?: throw NetworkException.Transport("no discovery provider handles $params")

        if (provider.id in running.value) return@netRunCatching emptyList()

        running.toggle(provider.id, add = true)
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
            running.toggle(provider.id, add = false)
        }

        found.values.toList()
    }

    override fun stopScan(id: SpiId) {
        scanJobs.remove(id)?.cancel()
        running.toggle(id, add = false)
    }

    override suspend fun startAdvertising(id: SpiId): Result<Unit> = netRunCatching {
        advertisingLock.withLock {
            val config = config
            val advertiser = config.advertisers.firstOrNull { it.id == id }
                ?: throw NetworkException.Transport("no advertiser installed for ${id.value}")

            // An advertiser that gave up leaves a finished job behind; treating that as "already
            // advertising" would make every later call a no-op and the device would stay invisible.
            if (advertisingJobs[id]?.isActive == true) return@withLock

            launchAdvertiser(config, advertiser)
        }
    }

    override suspend fun stopAdvertising(id: SpiId) {
        advertisingLock.withLock { haltAdvertisers(listOf(id)) }
    }

    override suspend fun stopAdvertising() {
        advertisingLock.withLock { haltAdvertisers(advertisingJobs.keys.toList()) }
        config.logger.debug("advertising stopped")
    }

    /**
     * Reconciles what the previous config started. Scans and advertisers on an SPI that dropped
     * out are stopped, and the advertisers still on the air are restarted only if what this device
     * would say - or who would say it - actually changed. A reload is not a request to advertise:
     * an advertiser the host never started stays off.
     */
    override suspend fun onConfigChanged(old: NetworkConfig<*>, new: NetworkConfig<*>) {
        val installed = new.discoveryProviders.mapTo(mutableSetOf()) { it.id }
        scanJobs.keys.filterNot { it in installed }.forEach(::stopScan)

        advertisingLock.withLock {
            val advertisers = new.advertisers.associateBy { it.id }
            val onAir = advertisingJobs.keys.toList()

            // An advertiser the new config no longer installs cannot be re-announced, only stopped.
            haltAdvertisers(onAir.filterNot { it in advertisers })

            val restartable = onAir.filter { it in advertisers }
            if (restartable.isEmpty()) return@withLock

            val unchanged = old.advertisers == new.advertisers &&
                    old.policy.advertisement == new.policy.advertisement &&
                    advertisement(old) == advertisement(new)
            if (unchanged) return@withLock

            haltAdvertisers(restartable)
            restartable.forEach { launchAdvertiser(new, advertisers.getValue(it)) }
        }
    }

    override fun peer(deviceId: String): Flow<DiscoveredPeer?> = registry.peers
        .map { it[deviceId] }
        .distinctUntilChanged()

    /** Caller holds [advertisingLock]. */
    private suspend fun launchAdvertiser(config: NetworkConfig<*>, advertiser: Advertiser) {
        if (!config.policy.advertisement.enabled) {
            config.logger.debug("advertising is off; this device will not announce itself")
            return
        }

        config.logger.debug("starting advertising over ${advertiser.id.value}")

        val payload = advertisement(config)
        val job = scope.launch {
            try {
                advertiser.advertise(payload).collect { event ->
                    if (event is Advertiser.Event.Failed) {
                        config.logger.warn("advertiser ${advertiser.id.value} failed", event.cause)
                    }
                }
            } catch (e: Exception) {
                config.logger.error("advertiser ${advertiser.id.value} failed", e)
            }
        }

        advertisingJobs[advertiser.id] = job
        advertising.toggle(advertiser.id, add = true)

        // Registered last on purpose: an advertiser whose flow is already done runs this
        // immediately, and it has to undo the two lines above rather than race them.
        job.invokeOnCompletion {
            advertisingJobs.remove(advertiser.id, job)
            advertising.toggle(advertiser.id, add = false)
            config.logger.debug("advertiser ${advertiser.id.value} is off the air")
        }
    }

    /**
     * Waits for the advertisers to be gone rather than merely told to go.
     * Caller should hold [advertisingLock].
     */
    private suspend fun haltAdvertisers(ids: Collection<SpiId>) {
        ids.forEach { id ->
            advertisingJobs.remove(id)?.cancelAndJoin()
            advertising.toggle(id, add = false)
        }
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

    private fun MutableStateFlow<Set<SpiId>>.toggle(id: SpiId, add: Boolean) {
        update { if (add) it + id else it - id }
    }
}
