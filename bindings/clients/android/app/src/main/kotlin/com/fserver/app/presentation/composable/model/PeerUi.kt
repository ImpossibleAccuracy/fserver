package com.fserver.app.presentation.composable.model

import androidx.compose.runtime.Immutable
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.device.model.DeviceKind
import com.fserver.core.network.device.model.ForeignDevice
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlin.time.Instant
import kotlin.time.toKotlinInstant

/** Another device, as any screen names it: live when around, from its trust record otherwise. */
@Immutable
data class PeerUi(
    val id: String = "",
    val name: String = "",
    val kind: DeviceKind? = null,
    /** A session is up. */
    val online: Boolean = false,
    val lastSeen: Instant? = null,
)

fun ForeignDevice.toPeerUi() = PeerUi(
    id = deviceId,
    name = displayName,
    kind = kind,
    online = hasSession,
    lastSeen = lastSeen?.toKotlinInstant(),
)

/** Every visible or trusted device, keyed by id. */
fun DevicesRepository.peers(): Flow<Map<String, PeerUi>> =
    devices.all.map { list -> list.associate { it.deviceId to it.toPeerUi() } }

/** A device neither visible nor trusted is named by its id. */
fun Map<String, PeerUi>.peerOf(deviceId: String): PeerUi =
    this[deviceId] ?: PeerUi(id = deviceId, name = deviceId)
