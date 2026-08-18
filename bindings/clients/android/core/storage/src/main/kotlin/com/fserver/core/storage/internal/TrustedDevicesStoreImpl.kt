package com.fserver.core.storage.internal

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.fserver.core.network.TransportKind
import com.fserver.core.network.auth.AuthMethod
import com.fserver.core.network.device.model.KnownRoute
import com.fserver.core.network.device.model.TrustedDevice
import com.fserver.core.storage.TrustedDevicesRepository
import com.fserver.core.storage.database.FServerStorageDatabase
import com.fserver.core.store.TrustedDevicesStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant
import com.fserver.core.storage.database.KnownRoute as DBKnownRoute
import com.fserver.core.storage.database.TrustedDevice as DBTrustedDevice

internal class TrustedDevicesStoreImpl(
    database: FServerStorageDatabase,
) : TrustedDevicesStore, TrustedDevicesRepository {
    private val dao = database.trustedDeviceQueries
    private val routeDao = database.knownRouteQueries

    override val devices: Flow<List<TrustedDevice>> = dao.selectAll()
        .asFlow()
        .mapToList(Dispatchers.IO)
        .map { rows -> rows.map { it.toDomainModel() } }

    override suspend fun findByKey(publicKey: ByteArray): TrustedDevice? =
        dao.findByKey(publicKey).executeAsOneOrNull()?.toDomainModel()

    override suspend fun findByDeviceId(deviceId: String): List<TrustedDevice> =
        dao.findByDeviceId(deviceId).executeAsList().map { it.toDomainModel() }

    override suspend fun upsert(record: TrustedDevice) {
        dao.upsert(
            publicKey = record.publicKey,
            deviceId = record.deviceId,
            displayName = record.displayName,
            method = record.method.name,
            strength = record.strength,
            lastSeenEpochMs = record.lastSeen.toEpochMilliseconds(),
        )
    }

    // TODO: this solution needs full rewrite, starting from usage TransportKind as transport, and finishing multiple upsert calls
    override suspend fun recordKnownRoute(deviceId: String, route: KnownRoute) {
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
    }

    override fun findKnownRoute(deviceId: String): Flow<KnownRoute?> =
        routeDao.selectByDeviceId(deviceId)
            .asFlow()
            .mapToOneOrNull(Dispatchers.IO)
            .map { it?.toDomainModel() }

    /** The trigger on `trustedDevice` drops the route once the device has no keys left. */
    override suspend fun forget(deviceId: String) {
        dao.deleteByDeviceId(deviceId)
    }
}

private fun DBTrustedDevice.toDomainModel() = TrustedDevice(
    publicKey = publicKey,
    deviceId = deviceId,
    displayName = displayName,
    method = AuthMethod.valueOf(method),
    strength = strength,
    lastSeen = Instant.fromEpochMilliseconds(lastSeenEpochMs),
)

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
