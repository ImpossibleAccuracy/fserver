package com.fserver.core.storage.internal

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.fserver.core.network.TransportKind
import com.fserver.core.network.auth.AuthMethod
import com.fserver.core.network.device.model.DeviceKind
import com.fserver.core.network.device.model.DeviceMetadata
import com.fserver.core.network.device.model.FailedContact
import com.fserver.core.network.device.model.KnownRoute
import com.fserver.core.network.device.model.TrustedDevice
import com.fserver.core.storage.TrustedDevicesRepository
import com.fserver.core.storage.database.FServerStorageDatabase
import com.fserver.core.store.network.TrustedDevicesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant
import com.fserver.core.storage.database.FailedContact as DBFailedContact
import com.fserver.core.storage.database.KnownRoute as DBKnownRoute

internal class TrustedDevicesStoreImpl(
    private val database: FServerStorageDatabase,
) : TrustedDevicesStore, TrustedDevicesRepository {
    private val dao = database.trustedDeviceQueries
    private val metadataDao = database.deviceMetadataQueries
    private val routeDao = database.knownRouteQueries
    private val failedContactDao = database.failedContactQueries

    override val devices: Flow<List<TrustedDevice>> = dao.selectAll(::trustedDeviceOf)
        .asFlow()
        .mapToList(Dispatchers.IO)

    override val knownDeviceIds: Flow<Set<String>> = dao.selectDeviceIds()
        .asFlow()
        .mapToList(Dispatchers.IO)
        .map { it.toSet() }

    override suspend fun findByKey(publicKey: ByteArray): TrustedDevice? =
        dao.findByKey(publicKey, ::trustedDeviceOf).executeAsOneOrNull()

    override suspend fun findByDeviceId(deviceId: String): List<TrustedDevice> =
        dao.findByDeviceId(deviceId, ::trustedDeviceOf).executeAsList()

    /**
     * The key's own record and the device's, in one transaction: a screen reading half of a pin is
     * a device that has a name but no kind, or a kind stamped against no key at all.
     */
    override suspend fun upsert(record: TrustedDevice) {
        database.transaction {
            dao.upsert(
                publicKey = record.publicKey,
                deviceId = record.deviceId,
                displayName = record.displayName,
                method = record.method.name,
                strength = record.strength,
            )

            record.metadata?.let { metadata ->
                metadataDao.pinClaims(
                    deviceId = record.deviceId,
                    kind = metadata.kind?.name,
                    dictionaryId = metadata.dictionaryId,
                    dictionaryVersion = metadata.dictionaryVersion?.toLong(),
                    lastSeenEpochMs = metadata.lastSeen?.toEpochMilliseconds(),
                )
            }
        }
    }

    override suspend fun recordLastNetwork(deviceId: String, networkId: String?) {
        metadataDao.updateLastNetwork(deviceId = deviceId, lastNetworkId = networkId)
    }

    // TODO: this solution needs full rewrite, starting from usage TransportKind as transport, and finishing multiple upsert calls
    override suspend fun recordKnownRoute(deviceId: String, route: KnownRoute, networkId: String?) {
        when (route) {
            is KnownRoute.Ip -> routeDao.upsert(
                deviceId = deviceId,
                kind = KnownRouteKind.Ip.name,
                isDialable = if (route.isDialable) 1 else 0,
                transport = route.transport.dbName,
                line1 = route.host,
                line2 = route.port?.toString(),
            )

            is KnownRoute.Nearby -> routeDao.upsert(
                deviceId = deviceId,
                kind = KnownRouteKind.Nearby.name,
                isDialable = if (route.isDialable) 1 else 0,
                transport = route.transport.dbName,
                line1 = route.endpointId,
                line2 = null,
            )
        }

        metadataDao.updateLastNetwork(deviceId = deviceId, lastNetworkId = networkId)
    }

    override suspend fun findKnownRoute(deviceId: String): KnownRoute? =
        routeDao.selectByDeviceId(deviceId)
            .executeAsOneOrNull()
            ?.toDomainModel()

    override fun observeKnownRoute(deviceId: String): Flow<KnownRoute?> =
        routeDao.selectByDeviceId(deviceId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.toDomainModel() }

    override val failedContacts: Flow<List<FailedContact>> = failedContactDao.selectAll()
        .asFlow()
        .mapToList(Dispatchers.IO)
        .map { rows -> rows.mapNotNull { it.toDomainModel() } }

    override suspend fun findFailedContact(deviceId: String): FailedContact? =
        failedContactDao.selectByDeviceId(deviceId)
            .executeAsOneOrNull()
            ?.toDomainModel()

    /** The query writes nothing for a device with no key on record - see `FailedContact.sq`. */
    override suspend fun recordFailedContact(contact: FailedContact) {
        failedContactDao.upsert(
            deviceId = contact.deviceId,
            reason = contact.reason.name,
            failedAtEpochMs = contact.failedAt.toEpochMilliseconds(),
            sinceEpochMs = contact.since.toEpochMilliseconds(),
            attempts = contact.attempts.toLong(),
            transport = contact.transport?.dbName,
            detail = contact.detail,
        )
    }

    override suspend fun clearFailedContact(deviceId: String) {
        failedContactDao.deleteByDeviceId(deviceId)
    }

    /** The trigger on `trustedDevice` drops the route, the metadata and the run with the last key. */
    override suspend fun forget(deviceId: String) {
        dao.deleteByDeviceId(deviceId)
    }
}

/**
 * One mapper for every read on `trustedDevice`: they all select the same joined columns in the same
 * order, so the argument list below is the contract those queries have to keep.
 *
 * [metadataDeviceId] is the join marker - null means no `deviceMetadata` row, which is not the same
 * as a row whose every column happens to be null.
 */
private fun trustedDeviceOf(
    publicKey: ByteArray,
    deviceId: String,
    displayName: String,
    method: String,
    strength: String,
    metadataDeviceId: String?,
    kind: String?,
    dictionaryId: String?,
    dictionaryVersion: Long?,
    lastSeenEpochMs: Long?,
    lastNetworkId: String?,
) = TrustedDevice(
    publicKey = publicKey,
    deviceId = deviceId,
    displayName = displayName,
    method = AuthMethod.valueOf(method),
    strength = strength,
    metadata = metadataDeviceId?.let {
        DeviceMetadata(
            // Tolerates a kind written by a newer build, which is not a reason to fail a read.
            kind = DeviceKind.entries.firstOrNull { it.name == kind },
            dictionaryId = dictionaryId,
            dictionaryVersion = dictionaryVersion?.toInt(),
            lastSeen = lastSeenEpochMs?.let(Instant::fromEpochMilliseconds),
            lastNetworkId = lastNetworkId,
        )
    },
)

/**
 * null for a reason written by a newer build: a run nothing here can describe is no more use than
 * no run at all, and dropping it reads as "reached, or never tried" rather than as a broken row.
 */
private fun DBFailedContact.toDomainModel(): FailedContact? {
    val parsed = FailedContact.Reason.entries.firstOrNull { it.name == reason } ?: return null

    return FailedContact(
        deviceId = deviceId,
        reason = parsed,
        failedAt = Instant.fromEpochMilliseconds(failedAtEpochMs),
        since = Instant.fromEpochMilliseconds(sinceEpochMs),
        attempts = attempts.toInt(),
        transport = transport?.let { name -> TransportKind.entries.firstOrNull { it.dbName == name } },
        detail = detail,
    )
}

private enum class KnownRouteKind { Ip, Nearby }

private val TransportKind.dbName: String
    get() = when (this) {
        TransportKind.MulticastDns -> "mdns"
        TransportKind.NearbyConnections -> "nearby"
        TransportKind.ManualAddress -> "ip"
        TransportKind.SubnetScan -> "subnetscan"
    }

/** null for a kind written by a newer build - an unusable route reads the same as none at all. */
private fun DBKnownRoute.toDomainModel(): KnownRoute? = when (KnownRouteKind.valueOf(kind)) {
    KnownRouteKind.Ip -> KnownRoute.Ip(
        transport = TransportKind.entries.first { it.dbName == transport },
        host = line1,
        port = line2?.toInt(),
        isDialable = isDialable == 1L,
    )

    KnownRouteKind.Nearby -> KnownRoute.Nearby(
        endpointId = line1,
    )
}
