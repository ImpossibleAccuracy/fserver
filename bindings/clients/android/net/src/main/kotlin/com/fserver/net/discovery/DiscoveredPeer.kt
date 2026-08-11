package com.fserver.net.discovery

import com.fserver.net.connection.PeerRef
import com.fserver.net.security.Fingerprint
import java.time.Instant

enum class DeviceKind { Desktop, Laptop, Phone, Tablet, Nas }

/** How the peer says it gates connections. A hint for the UI; the peer enforces it, not us. */
enum class AccessMode { Open, Password, Key }

/**
 * A device as discovery sees it. One entry per device, however many methods found it - the
 * laptop that answers on both mDNS and Nearby is one row with two [routes].
 */
data class DiscoveredPeer(
    val deviceId: String,
    val displayName: String,
    val kind: DeviceKind?,
    val routes: List<PeerRef>,
    val advertised: AdvertisedInfo,
    val lastSeen: Instant,
)

/** What the peer announced before anyone connected. Not verified - the handshake does that. */
data class AdvertisedInfo(
    val protocolVersions: IntRange? = null,
    val fingerprint: Fingerprint? = null,
    val accessMode: AccessMode? = null,
    val dictionaryId: String? = null,
    val dictionaryVersion: Int? = null,
)

/**
 * Keys `:net` reads out of [com.fserver.net.spi.DiscoveredEndpoint.attributes] and writes into an
 * advertisement. Transport that uses different keys simply produces peers with less known
 * about them - nothing breaks.
 */
object PeerAttributes {
    const val DEVICE_ID = "did"
    const val DISPLAY_NAME = "name"
    const val KIND = "kind"
    const val FINGERPRINT = "fp"
    const val PROTOCOL_MIN = "pmin"
    const val PROTOCOL_MAX = "pmax"
    const val ACCESS = "access"
    const val DICTIONARY_ID = "dict"
    const val DICTIONARY_VERSION = "dictv"
}
