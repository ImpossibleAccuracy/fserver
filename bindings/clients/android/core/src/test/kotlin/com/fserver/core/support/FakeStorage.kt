package com.fserver.core.support

import com.fserver.core.sync.metadata.PeerSourceMetadata
import com.fserver.core.files.SourceLocation
import com.fserver.core.network.auth.OfferedAuthMethod
import com.fserver.core.network.device.model.FailedContact
import com.fserver.core.network.device.model.KnownRoute
import com.fserver.core.network.device.model.LocalDevice
import com.fserver.core.network.device.model.TrustedDevice
import com.fserver.core.store.FServerStorage
import com.fserver.core.store.FServerStorageApi
import com.fserver.core.store.network.AuthSettingsStore
import com.fserver.core.store.network.DeviceIdentityStore
import com.fserver.core.store.network.TrustedDevicesStore
import com.fserver.core.store.sync.ConflictDecisionsStore
import com.fserver.core.store.sync.FileIndexStore
import com.fserver.core.store.sync.RemoteIndexStore
import com.fserver.core.store.sync.SourceRequestsStore
import com.fserver.core.store.sync.SourcesStore
import com.fserver.core.store.sync.SyncStore
import com.fserver.core.store.sync.UploadStagingStore
import com.fserver.core.sync.conflict.ConflictDecision
import com.fserver.core.sync.version.HlcTimestamp
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.index.RemoteIndexedFile
import com.fserver.core.sync.limits.SourceUsage
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SourceTombstone
import com.fserver.core.sync.model.StagedUpload
import com.fserver.core.sync.model.SyncMode
import com.fserver.core.sync.setup.IncomingSourceRequest
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import java.security.KeyPair
import java.security.KeyPairGenerator
import kotlin.time.Instant

/**
 * The whole storage SPI, in memory.
 *
 * `@OptIn` is deliberate and confined to this file: the rule that keeps a store out of `:app`
 * exists so a UI never reaches past its repositories, and a fake standing in for the host's
 * backend inside `:core`'s own tests is the case the rule is not about. Nothing here is published.
 */
@OptIn(FServerStorageApi::class)
internal class FakeStorage(
    localDeviceId: String = "device-local",
    private val clock: MutableTimeProvider = MutableTimeProvider(),
) : FServerStorage {
    override val identity: FakeIdentityStore = FakeIdentityStore(localDeviceId)
    override val auth: FakeAuthSettingsStore = FakeAuthSettingsStore()
    override val trust: FakeTrustedDevicesStore = FakeTrustedDevicesStore()
    override val index: FakeFileIndexStore = FakeFileIndexStore()
    override val remoteIndex: FakeRemoteIndexStore = FakeRemoteIndexStore()
    override val sources: FakeSourcesStore = FakeSourcesStore(clock, index)
    override val sourceRequests: FakeSourceRequestsStore = FakeSourceRequestsStore()
    override val preferences: FakeSyncStore = FakeSyncStore()
    override val uploads: FakeUploadStagingStore = FakeUploadStagingStore()
    override val conflictDecisions: FakeConflictDecisionsStore = FakeConflictDecisionsStore()
}

@OptIn(FServerStorageApi::class)
internal class FakeIdentityStore(private val deviceId: String) : DeviceIdentityStore {
    override val identityKeyPair: KeyPair by lazy {
        KeyPairGenerator.getInstance("EC").genKeyPair()
    }

    override suspend fun localDevice(): LocalDevice = LocalDevice(
        deviceId = deviceId,
        displayName = "Local device",
        kind = null,
    )
}

@OptIn(FServerStorageApi::class)
internal class FakeAuthSettingsStore : AuthSettingsStore {
    private val _offeredMethods = MutableStateFlow<List<OfferedAuthMethod>>(emptyList())
    override val offeredMethods: StateFlow<List<OfferedAuthMethod>> = _offeredMethods
}

@OptIn(FServerStorageApi::class)
internal class FakeTrustedDevicesStore : TrustedDevicesStore {
    private val _devices = MutableStateFlow<List<TrustedDevice>>(emptyList())
    override val devices: Flow<List<TrustedDevice>> = _devices

    override val knownDeviceIds: Flow<Set<String>> =
        _devices.map { list -> list.mapTo(mutableSetOf()) { it.deviceId } }

    /** Routes and networks written by [recordKnownRoute] / [recordLastNetwork], for assertions. */
    val routes: MutableMap<String, KnownRoute> = mutableMapOf()
    val networks: MutableMap<String, String?> = mutableMapOf()

    private val contacts = MutableStateFlow<Map<String, FailedContact>>(emptyMap())
    override val failedContacts: Flow<List<FailedContact>> = contacts.map { it.values.toList() }

    override suspend fun findByKey(publicKey: ByteArray): TrustedDevice? =
        _devices.value.find { it.publicKey.contentEquals(publicKey) }

    override suspend fun findByDeviceId(deviceId: String): List<TrustedDevice> =
        _devices.value.filter { it.deviceId == deviceId }

    override suspend fun upsert(record: TrustedDevice) {
        _devices.update { current ->
            current.filterNot { it.publicKey.contentEquals(record.publicKey) } + record
        }
    }

    override suspend fun recordKnownRoute(deviceId: String, route: KnownRoute, networkId: String?) {
        routes[deviceId] = route
        networks[deviceId] = networkId
    }

    override suspend fun findKnownRoute(deviceId: String): KnownRoute? = routes[deviceId]

    override suspend fun recordLastNetwork(deviceId: String, networkId: String?) {
        networks[deviceId] = networkId
    }

    override suspend fun findFailedContact(deviceId: String): FailedContact? =
        contacts.value[deviceId]

    /** Same guard the real backend puts in SQL: nothing is kept for a device with no key on record. */
    override suspend fun recordFailedContact(contact: FailedContact) {
        if (_devices.value.none { it.deviceId == contact.deviceId }) return

        contacts.update { it + (contact.deviceId to contact) }
    }

    override suspend fun clearFailedContact(deviceId: String) {
        contacts.update { it - deviceId }
    }
}

@OptIn(FServerStorageApi::class)
internal class FakeFileIndexStore : FileIndexStore {
    private val rows = MutableStateFlow<List<LocalIndexedFile>>(emptyList())
    override val all: Flow<List<LocalIndexedFile>> = rows

    val current: List<LocalIndexedFile> get() = rows.value

    override suspend fun findFile(key: IndexedFileKey): LocalIndexedFile? =
        rows.value.find { it.fileId == key.fileId && it.sourceId == key.sourceId }

    override suspend fun processedFiles(sourceId: String): List<LocalIndexedFile> =
        rows.value.filter { it.sourceId == sourceId }

    override suspend fun presentUsage(sourceId: String): SourceUsage =
        rows.value.filter { it.sourceId == sourceId && it.state is LocalIndexedFile.State.Present }
            .let { present -> SourceUsage(files = present.size, bytes = present.sumOf { it.size.bytes }) }

    override suspend fun markProcessed(indexed: Collection<LocalIndexedFile>) {
        val ids = indexed.mapTo(mutableSetOf()) { it.id }
        rows.update { current -> current.filterNot { it.id in ids } + indexed }
    }

    override suspend fun updateFileState(key: IndexedFileKey, state: LocalIndexedFile.State) {
        replace(key) { it.copy(state = state) }
    }

    override suspend fun clearProcessed(sourceId: String) {
        rows.update { current -> current.filterNot { it.sourceId == sourceId } }
    }

    private fun replace(key: IndexedFileKey, edit: (LocalIndexedFile) -> LocalIndexedFile) {
        rows.update { current ->
            current.map {
                if (it.fileId == key.fileId && it.sourceId == key.sourceId) edit(it) else it
            }
        }
    }
}

@OptIn(FServerStorageApi::class)
internal class FakeRemoteIndexStore : RemoteIndexStore {
    private val rows = MutableStateFlow<List<RemoteIndexedFile>>(emptyList())
    override val all: Flow<List<RemoteIndexedFile>> = rows

    /** What [replace] was told the source belongs to, keyed by source id. */
    val attributedTo: MutableMap<String, String> = mutableMapOf()

    override suspend fun files(sourceId: String): List<RemoteIndexedFile> =
        rows.value.filter { it.sourceId == sourceId }

    override suspend fun replace(
        sourceId: String,
        deviceId: String,
        files: Collection<RemoteIndexedFile>,
    ) {
        attributedTo[sourceId] = deviceId
        // Mirrors the real backend: the row is stored under the source the caller authorized,
        // never under whatever source id the peer wrote inside the record.
        val stored = files.map { it.copy(sourceId = sourceId) }
        rows.update { current -> current.filterNot { it.sourceId == sourceId } + stored }
    }

    override suspend fun upsert(deviceId: String, file: RemoteIndexedFile) {
        attributedTo[file.sourceId] = deviceId
        rows.update { current ->
            current.filterNot { it.sourceId == file.sourceId && it.fileId == file.fileId } + file
        }
    }

    override suspend fun clear(sourceId: String) {
        rows.update { current -> current.filterNot { it.sourceId == sourceId } }
    }
}

@OptIn(FServerStorageApi::class)
internal class FakeSourcesStore(
    private val clock: MutableTimeProvider,
    private val index: FakeFileIndexStore,
) : SourcesStore {
    private val entries = linkedMapOf<String, SourceEntry>()
    private val tombstones = mutableMapOf<String, SourceTombstone>()

    override suspend fun all(): List<SourceEntry> = entries.values.toList()

    override suspend fun findById(id: String): SourceEntry? = entries[id]

    override suspend fun findByModeAndLocation(
        mode: SyncMode,
        location: SourceLocation,
    ): SourceEntry? = entries.values.find { it.syncMode == mode && it.location == location }

    override suspend fun upsert(source: SourceEntry) {
        entries[source.id] = source
    }

    override suspend fun markSynced(id: String, at: Instant) {
        entries[id]?.let { entries[id] = it.copy(lastSyncedAt = at) }
    }

    override suspend fun updateStatus(id: String, status: SourceEntry.Status) {
        entries[id]?.let { entries[id] = it.copy(status = status) }
    }

    override suspend fun updatePreferences(id: String, preferences: SourceEntry.Preferences) {
        entries[id]?.let { entries[id] = it.copy(preferences = preferences) }
    }

    private val metadata = mutableMapOf<Pair<String, String>, PeerSourceMetadata>()

    override suspend fun recordMetadata(metadata: PeerSourceMetadata) {
        this.metadata[metadata.sourceId to metadata.deviceId] = metadata
    }

    /** What [deviceId] last reported about [sourceId]. */
    fun metadataOf(sourceId: String, deviceId: String): PeerSourceMetadata? = metadata[sourceId to deviceId]

    override suspend fun delete(id: String) {
        val removed = entries.remove(id) ?: return
        index.clearProcessed(id)
        metadata.keys.removeAll { it.first == id }
        tombstones[id] = SourceTombstone(
            sourceId = id,
            deviceId = removed.deviceId,
            removedAt = clock.now(),
            location = removed.location,
        )
    }

    override suspend fun recordRefusal(id: String, deviceId: String) {
        tombstones[id] = SourceTombstone(
            sourceId = id,
            deviceId = deviceId,
            removedAt = clock.now(),
            location = null,
        )
    }

    override suspend fun findTombstone(id: String): SourceTombstone? = tombstones[id]

    /** Lets a test plant a tombstone without going through a live source first. */
    fun putTombstone(sourceId: String, deviceId: String, removedAt: Instant = TestEpoch) {
        tombstones[sourceId] = SourceTombstone(
            sourceId = sourceId,
            deviceId = deviceId,
            removedAt = removedAt,
            location = SourceLocation.Internal(bucket = sourceId),
        )
    }
}

@OptIn(FServerStorageApi::class)
internal class FakeSourceRequestsStore : SourceRequestsStore {
    private val requests = MutableStateFlow<List<IncomingSourceRequest>>(emptyList())

    override fun pending(): Flow<List<IncomingSourceRequest>> =
        requests.map { list -> list.sortedBy { it.receivedAt } }

    val current: List<IncomingSourceRequest> get() = requests.value

    override suspend fun findById(sourceId: String): IncomingSourceRequest? =
        requests.value.find { it.sourceId == sourceId }

    override suspend fun upsert(request: IncomingSourceRequest) {
        requests.update { current ->
            current.filterNot { it.sourceId == request.sourceId } + request
        }
    }

    override suspend fun delete(sourceId: String) {
        requests.update { current -> current.filterNot { it.sourceId == sourceId } }
    }
}

@OptIn(FServerStorageApi::class)
internal class FakeSyncStore : SyncStore {
    var clock: HlcTimestamp? = null

    override suspend fun loadClock(): HlcTimestamp? = clock

    override suspend fun saveClock(timestamp: HlcTimestamp) {
        clock = timestamp
    }
}

@OptIn(FServerStorageApi::class)
internal class FakeUploadStagingStore : UploadStagingStore {
    private val rows = linkedMapOf<IndexedFileKey, StagedUpload>()

    override suspend fun all(): List<StagedUpload> = rows.values.toList()

    override suspend fun find(key: IndexedFileKey): StagedUpload? = rows[key]

    override suspend fun upsert(upload: StagedUpload) {
        rows[IndexedFileKey(fileId = upload.fileId, sourceId = upload.sourceId)] = upload
    }

    override suspend fun checkpoint(key: IndexedFileKey, offset: Long, at: Instant) {
        rows[key]?.let { rows[key] = it.copy(committedOffset = offset, touchedAt = at) }
    }

    override suspend fun delete(key: IndexedFileKey) {
        rows.remove(key)
    }
}

@OptIn(FServerStorageApi::class)
internal class FakeConflictDecisionsStore : ConflictDecisionsStore {
    private val rows = MutableStateFlow<Map<IndexedFileKey, ConflictDecision>>(emptyMap())
    override val all: Flow<List<ConflictDecision>> = rows.map { it.values.toList() }

    override suspend fun find(key: IndexedFileKey): ConflictDecision? = rows.value[key]

    override suspend fun forSource(sourceId: String): List<ConflictDecision> =
        rows.value.values.filter { it.sourceId == sourceId }

    override suspend fun put(decision: ConflictDecision) {
        rows.update { it + (IndexedFileKey(fileId = decision.fileId, sourceId = decision.sourceId) to decision) }
    }

    override suspend fun remove(key: IndexedFileKey) {
        rows.update { it - key }
    }
}
