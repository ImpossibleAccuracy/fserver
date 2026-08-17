package com.fserver.app.data

import app.cash.sqldelight.coroutines.asFlow
import app.cash.sqldelight.coroutines.mapToList
import app.cash.sqldelight.coroutines.mapToOneOrNull
import com.fserver.app.database.FServerDatabase
import com.fserver.app.domain.SavedDevicesRepository
import com.fserver.core.network.auth.AuthMethod
import com.fserver.core.network.device.model.KnownRoute
import com.fserver.core.network.device.model.TrustedDevice
import com.fserver.core.network.info.DetectionMethod
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant
import com.fserver.app.database.KnownRoute as DBKnownRoute
import com.fserver.app.database.TrustedDevice as DBTrustedDevice

internal class SavedDevicesRepositoryImpl(
    database: FServerDatabase,
) : SavedDevicesRepository {
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

    // TODO: this solution needs full rewrite, starting from usage DetectionMethod as transport, and finishing multiple upsert calls
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
    override suspend fun delete(publicKey: ByteArray) {
        dao.deleteByKey(publicKey)
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

private val DetectionMethod.dbName: String
    get() = when (this) {
        DetectionMethod.Automatic.MulticastDns -> "mdns"
        DetectionMethod.Automatic.NearbyConnections -> "nearby"
        DetectionMethod.OnDemand.ManualAddress -> "ip"
        DetectionMethod.OnDemand.SubnetScan -> "subnetscan"
    }

/** null for a kind written by a newer build - an unusable route reads the same as none at all. */
private fun DBKnownRoute.toDomainModel(): KnownRoute? = when (KnownRouteKind.valueOf(kind)) {
    KnownRouteKind.Ip -> KnownRoute.Ip(
        transport = DetectionMethod.entries.first { it.dbName == transport },
        host = line1,
        port = line2?.toInt(),
        isDialable = isDialable == 1L,
    )

    KnownRouteKind.Nearby -> KnownRoute.Nearby(
        endpointId = line1,
    )
}
