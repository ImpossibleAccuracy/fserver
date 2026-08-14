package com.fserver.net.discovery

import com.fserver.net.connection.PeerRef
import com.fserver.net.peer.PeerDescriptor
import com.fserver.net.security.auth.AuthMethodId
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
            advertised = AdvertisedPeer(
                deviceId = deviceId,
                displayName = attributes[PeerAttributes.DISPLAY_NAME] ?: endpoint.advertisedName,
                kind = attributes[PeerAttributes.KIND]?.let(::parseKind),
                protocolVersions = versionRange(attributes),
                methods = parseMethods(attributes[PeerAttributes.AUTH_METHODS]),
            ),
            routes = listOf(route),
            lastSeen = Instant.now(),
        )

        known.update { current ->
            val merged = current[deviceId]
                ?.let { existing ->
                    peer.copy(
                        routes = existing.routes
                            .filterNot { it.endpoint.address == route.endpoint.address }
                            .plus(route),
                    )
                }
                ?: peer

            current + (deviceId to merged)
        }

        return known.value.getValue(deviceId)
    }

    /** A peer went away on one address; the device only disappears when its last route does. */
    fun forgetRoute(endpointAddress: String) {
        known.update { current ->
            current
                .mapValues { (_, peer) ->
                    peer.copy(
                        routes = peer.routes.filterNot {
                            it.endpoint.address == endpointAddress
                        }
                    )
                }
                .filterValues { it.routes.isNotEmpty() }
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

    private fun parseMethods(raw: String?): List<AuthMethodId> = raw
        ?.split(PeerAttributes.SEPARATOR)
        ?.map(String::trim)
        ?.filter(String::isNotEmpty)
        ?.map(::AuthMethodId)
        .orEmpty()

    /** By name, unlike [com.fserver.net.peer.PeerDescriptorCodec], which goes by ordinal. */
    private fun parseKind(raw: String): PeerDescriptor.Kind? =
        PeerDescriptor.Kind.entries.firstOrNull { it.name.equals(raw, ignoreCase = true) }
}
