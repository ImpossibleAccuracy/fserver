package com.fserver.net.discovery

import com.fserver.net.NetLogger
import com.fserver.net.NetworkException
import com.fserver.net.dictionary.MessageDictionary
import com.fserver.net.security.IdentityStore
import com.fserver.net.spi.Advertiser
import com.fserver.net.spi.DiscoveryProvider
import com.fserver.net.utils.netRunCatching
import com.fserver.net.wire.ProtocolVersions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class PeerDiscoveryImpl(
    private val providers: List<DiscoveryProvider>,
    private val advertisers: List<Advertiser>,
    private val identityStore: IdentityStore,
    private val dictionary: MessageDictionary.Descriptor,
    private val advertisedAttributes: Map<String, String>,
    private val logger: NetLogger,
    private val scope: CoroutineScope,
) : PeerDiscovery {

    private val registry = PeerRegistry()

    override val peers: StateFlow<List<DiscoveredPeer>> = registry.peers
        .map { it.values.toList() }
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    private val running = MutableStateFlow<Set<DiscoveryProvider.Id>>(emptySet())
    override val activeScans: StateFlow<Set<DiscoveryProvider.Id>> = running.asStateFlow()

    private val scanJobs = mutableMapOf<DiscoveryProvider.Id, Job>()
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
            provider.scan(params).collect { event ->
                when (event) {
                    is DiscoveryProvider.Event.Appeared -> {
                        val peer = registry.record(event.peer)
                        found[peer.deviceId] = peer
                    }

                    is DiscoveryProvider.Event.Disappeared -> registry.forgetRoute(event.endpointAddress)

                    is DiscoveryProvider.Event.Failed -> logger.warn(
                        "discovery ${provider.id.value} failed",
                        event.cause
                    )
                }
            }
        } finally {
            running.update(provider.id, add = false)
        }

        found.values.toList()
    }

    override fun stopScan(id: DiscoveryProvider.Id) {
        scanJobs.remove(id)?.cancel()
        running.update(id, add = false)
    }

    override suspend fun startAdvertising(): Result<Unit> = netRunCatching {
        // An advertiser that gave up leaves a finished job behind;
        // keeping it would make every later call a no-op and device would stay invisible.
        advertisingJobs = advertisingJobs.filter(Job::isActive)
        if (advertisingJobs.isNotEmpty()) return@netRunCatching

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
    }

    override fun peer(deviceId: String): Flow<DiscoveredPeer?> = registry.peers
        .map { it[deviceId] }
        .distinctUntilChanged()

    /** What this device puts on the wire about itself. Descriptive only - never a claim of access. */
    private fun advertisement(): Advertiser.Payload {
        val local = identityStore.local
        return Advertiser.Payload(
            identity = local,
            attributes = buildMap {
                put(PeerAttributes.DEVICE_ID, local.deviceId)
                put(PeerAttributes.DISPLAY_NAME, local.displayName)
                put(PeerAttributes.FINGERPRINT, local.fingerprint.value)
                put(PeerAttributes.PROTOCOL_MIN, ProtocolVersions.SUPPORTED.first.toString())
                put(PeerAttributes.PROTOCOL_MAX, ProtocolVersions.SUPPORTED.last.toString())
                put(PeerAttributes.DICTIONARY_ID, dictionary.id)
                put(PeerAttributes.DICTIONARY_VERSION, dictionary.version.toString())
                putAll(advertisedAttributes)
            },
        )
    }

    private fun MutableStateFlow<Set<DiscoveryProvider.Id>>.update(
        id: DiscoveryProvider.Id,
        add: Boolean
    ) {
        value = if (add) value + id else value - id
    }
}
