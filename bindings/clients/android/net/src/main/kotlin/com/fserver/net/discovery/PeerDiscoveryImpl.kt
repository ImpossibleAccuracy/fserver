package com.fserver.net.discovery

import com.fserver.net.NetLogger
import com.fserver.net.NetworkException
import com.fserver.net.config.AdvertisementPolicy
import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.security.identity.IdentityStore
import com.fserver.net.spi.Advertiser
import com.fserver.net.spi.DiscoveryProvider
import com.fserver.net.spi.SpiId
import com.fserver.net.utils.netRunCatching
import com.fserver.net.wire.ProtocolVersions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
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
import java.util.concurrent.ConcurrentHashMap

internal class PeerDiscoveryImpl(
    private val providers: List<DiscoveryProvider>,
    private val advertisers: List<Advertiser>,
    private val identityStore: IdentityStore,
    private val policy: AdvertisementPolicy,
    private val authMethods: List<AuthMethodId>,
    private val advertisedAttributes: Map<String, String>,
    private val logger: NetLogger,
    private val scope: CoroutineScope,
) : PeerDiscovery {

    private val registry = PeerRegistry()

    override val peers: StateFlow<List<DiscoveredPeer>> = registry.peers
        .map { it.values.toList() }
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    private val running = MutableStateFlow<Set<SpiId>>(emptySet())
    override val activeScans: StateFlow<Set<SpiId>> = running.asStateFlow()

    private val scanJobs = ConcurrentHashMap<SpiId, Job>()
    private var advertisingJobs: List<Job> = emptyList()

    override suspend fun scan(
        params: DiscoveryProvider.ScanParams
    ): Result<List<DiscoveredPeer>> = netRunCatching {
        val provider = providers.firstOrNull { it.accepts(params) }
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
                                if (event.peer.attributes[PeerAttributes.DEVICE_ID] == identityStore.local.deviceId) {
                                    // Discovery provider found its own device, ignore it
                                    return@collect
                                }

                                val peer = registry.record(event.peer)
                                found[peer.advertised.deviceId] = peer
                            }

                            is DiscoveryProvider.Event.Disappeared ->
                                registry.forgetRoute(event.endpointAddress)

                            is DiscoveryProvider.Event.Failed -> logger.warn(
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
        if (!policy.enabled) {
            logger.debug("advertising is off; this device will not announce itself")
            return@netRunCatching
        }

        // An advertiser that gave up leaves a finished job behind;
        // keeping it would make every later call a no-op and device would stay invisible.
        advertisingJobs = advertisingJobs.filter(Job::isActive)
        if (advertisingJobs.isNotEmpty()) return@netRunCatching

        logger.debug("starting advertising with ${advertisers.joinToString { it.id.value }}")

        val payload = advertisement()
        advertisingJobs = advertisers.map { advertiser ->
            scope.launch {
                advertiser.advertise(payload).collect { event ->
                    if (event is Advertiser.Event.Failed) {
                        logger.warn("advertiser ${advertiser.id.value} failed", event.cause)
                    }
                }
            }
        }
    }

    override fun stopAdvertising() {
        advertisingJobs.forEach(Job::cancel)
        advertisingJobs = emptyList()

        logger.debug("advertising stopped")
    }

    override fun peer(deviceId: String): Flow<DiscoveredPeer?> = registry.peers
        .map { it[deviceId] }
        .distinctUntilChanged()

    /**
     * What this device puts on the air about itself: the public greeting, plus a name to pick it
     * out of a list. Descriptive only - never a claim of access.
     *
     * Deliberately absent: the key fingerprint, which would let a passive listener follow this
     * device between networks, and the dictionary, which a connection reports better. Which of
     * the rest goes out is [policy]'s call, not this class's.
     *
     * Essential is what a peer needs to tell one device from another and know how to approach it;
     * the rest is decoration a transport short of room may leave off.
     */
    private fun advertisement(): Advertiser.Payload {
        val local = identityStore.local
        return Advertiser.Payload(
            identity = local,
            essential = buildMap {
                put(PeerAttributes.DEVICE_ID, local.deviceId)
                put(PeerAttributes.PROTOCOL_MIN, ProtocolVersions.SUPPORTED.first.toString())
                put(PeerAttributes.PROTOCOL_MAX, ProtocolVersions.SUPPORTED.last.toString())
                if (authMethods.isNotEmpty()) {
                    put(
                        PeerAttributes.AUTH_METHODS,
                        authMethods.joinToString(PeerAttributes.SEPARATOR) { it.value },
                    )
                }
                // Essential when published at all: telling one device from another in a list is
                // exactly what a name is for, and a transport short of room should not drop it
                // ahead of the decoration.
                if (policy.publishName) put(PeerAttributes.DISPLAY_NAME, local.displayName)
            },
            optional = buildMap {
                putAll(advertisedAttributes)
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
