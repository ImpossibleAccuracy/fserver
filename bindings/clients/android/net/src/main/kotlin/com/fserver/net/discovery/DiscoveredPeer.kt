package com.fserver.net.discovery

import com.fserver.net.connection.PeerRef
import com.fserver.net.security.Fingerprint
import java.time.Instant

/**
 * A device as discovery sees it. One entry per device, however many methods found it - the
 * laptop that answers on both mDNS and Nearby is one row with two [routes].
 */
data class DiscoveredPeer(
    val deviceId: String,
    val displayName: String,
    val kind: Kind?,
    val routes: List<PeerRef>,
    val advertised: Advertised,
    val lastSeen: Instant,
) {
    enum class Kind { Desktop, Laptop, Phone, Tablet, Nas }

    /** How the peer says it gates connections. A hint for the UI; the peer enforces it, not us. */
    enum class AccessMode { Open, Password, Key }

    /** What the peer announced before anyone connected. Not verified - the handshake does that. */
    data class Advertised(
        val protocolVersions: IntRange? = null,
        val fingerprint: Fingerprint? = null,
        val accessMode: AccessMode? = null,
        val dictionaryId: String? = null,
        val dictionaryVersion: Int? = null,
    )
}
