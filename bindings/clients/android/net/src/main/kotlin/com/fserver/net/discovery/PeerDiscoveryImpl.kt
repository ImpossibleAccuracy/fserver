package com.fserver.net.discovery

import com.fserver.net.NetLogger
import com.fserver.net.TransportException
import com.fserver.net.connection.netRunCatching
import com.fserver.net.dictionary.DictionaryDescriptor
import com.fserver.net.security.IdentityStore
import com.fserver.net.spi.Advertisement
import com.fserver.net.spi.Advertiser
import com.fserver.net.spi.AdvertisingEvent
import com.fserver.net.spi.DiscoveryId
import com.fserver.net.spi.DiscoveryProvider
import com.fserver.net.spi.PeerEvent
import com.fserver.net.spi.ScanParams
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
    private val dictionary: DictionaryDescriptor,
    private val advertisedAttributes: Map<String, String>,
    private val logger: NetLogger,
    private val scope: CoroutineScope,
) : PeerDiscovery {

    private val registry = PeerRegistry()

    override val peers: StateFlow<List<DiscoveredPeer>> = registry.peers
        .map { it.values.toList() }
        .stateIn(scope, SharingStarted.Lazily, emptyList())

    private val running = MutableStateFlow<Set<DiscoveryId>>(emptySet())
    override val activeScans: StateFlow<Set<DiscoveryId>> = running.asStateFlow()

    private val scanJobs = mutableMapOf<DiscoveryId, Job>()
    private var advertisingJobs: List<Job> = emptyList()

    override suspend fun scan(params: ScanParams): Result<List<DiscoveredPeer>> = netRunCatching {
        val provider = providers.firstOrNull { it.accepts(params) }
            ?: throw TransportException("no discovery provider handles $params")

        if (provider.id in running.value) return@netRunCatching emptyList()

        running.update(provider.id, add = true)
        val found = LinkedHashMap<String, DiscoveredPeer>()

        try {
            provider.scan(params).collect { event ->
                when (event) {
                    is PeerEvent.Appeared -> {
                        val peer = registry.record(event.peer)
                        found[peer.deviceId] = peer
                    }

                    is PeerEvent.Disappeared -> registry.forgetRoute(event.endpointAddress)

                    is PeerEvent.Failed -> logger.warn(
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

    override fun stopScan(id: DiscoveryId) {
        scanJobs.remove(id)?.cancel()
        running.update(id, add = false)
    }

    override suspend fun startAdvertising(): Result<Unit> = netRunCatching {
        if (advertisingJobs.isNotEmpty()) return@netRunCatching

        val payload = advertisement()
        advertisingJobs = advertisers.map { advertiser ->
            scope.launch {
                advertiser.advertise(payload).collect { event ->
                    if (event is AdvertisingEvent.Failed) {
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
    private fun advertisement(): Advertisement {
        val local = identityStore.local
        return Advertisement(
            deviceId = local.deviceId,
            displayName = local.displayName,
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

    private fun MutableStateFlow<Set<DiscoveryId>>.update(id: DiscoveryId, add: Boolean) {
        value = if (add) value + id else value - id
    }
}
