package com.fserver.net.discovery

import com.fserver.net.connection.PeerRef
import com.fserver.net.security.Fingerprint
import com.fserver.net.spi.DiscoveredEndpoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.time.Instant

/**
 * Folds every provider's events into one list of devices.
 *
 * De-duplication is by device id, so the same box found twice does not show up twice; a route it
 * announced under a new address replaces the old one instead of piling up.
 */
internal class PeerRegistry {

    private val known = MutableStateFlow<Map<String, DiscoveredPeer>>(emptyMap())
    val peers: StateFlow<Map<String, DiscoveredPeer>> = known.asStateFlow()

    fun record(endpoint: DiscoveredEndpoint): DiscoveredPeer {
        val attributes = endpoint.attributes
        val deviceId = attributes[PeerAttributes.DEVICE_ID] ?: endpoint.endpoint.address

        val route = PeerRef(
            deviceId = deviceId,
            transport = endpoint.endpoint.transport,
            endpoint = endpoint.endpoint,
        )

        val peer = DiscoveredPeer(
            deviceId = deviceId,
            displayName = attributes[PeerAttributes.DISPLAY_NAME] ?: endpoint.advertisedName,
            kind = attributes[PeerAttributes.KIND]?.let(::parseKind),
            routes = listOf(route),
            advertised = DiscoveredPeer.Advertised(
                protocolVersions = versionRange(attributes),
                fingerprint = attributes[PeerAttributes.FINGERPRINT]?.let(::Fingerprint),
                accessMode = attributes[PeerAttributes.ACCESS]?.let(::parseAccess),
                dictionaryId = attributes[PeerAttributes.DICTIONARY_ID],
                dictionaryVersion = attributes[PeerAttributes.DICTIONARY_VERSION]?.toIntOrNull(),
            ),
            lastSeen = Instant.now(),
        )

        known.update { current ->
            val merged = current[deviceId]?.let { existing ->
                peer.copy(
                    routes = (existing.routes.filterNot { it.endpoint.address == route.endpoint.address } + route),
                )
            } ?: peer
            current + (deviceId to merged)
        }

        return known.value.getValue(deviceId)
    }

    /** A peer went away on one address; the device only disappears when its last route does. */
    fun forgetRoute(endpointAddress: String) {
        known.update { current ->
            current.mapValues { (_, peer) ->
                peer.copy(routes = peer.routes.filterNot { it.endpoint.address == endpointAddress })
            }.filterValues { it.routes.isNotEmpty() }
        }
    }

    fun clear(transportFilter: (PeerRef) -> Boolean) {
        known.update { current ->
            current.mapValues { (_, peer) ->
                peer.copy(routes = peer.routes.filterNot(transportFilter))
            }.filterValues { it.routes.isNotEmpty() }
        }
    }

    private fun versionRange(attributes: Map<String, String>): IntRange? {
        val min = attributes[PeerAttributes.PROTOCOL_MIN]?.toIntOrNull() ?: return null
        val max = attributes[PeerAttributes.PROTOCOL_MAX]?.toIntOrNull() ?: return null
        return min..max
    }

    private fun parseKind(raw: String): DiscoveredPeer.Kind? =
        DiscoveredPeer.Kind.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }

    private fun parseAccess(raw: String): DiscoveredPeer.AccessMode? =
        DiscoveredPeer.AccessMode.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
}
