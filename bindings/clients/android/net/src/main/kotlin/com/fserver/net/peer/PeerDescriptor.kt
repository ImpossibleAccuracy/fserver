package com.fserver.net.peer

import com.fserver.net.security.Fingerprint

/**
 * What a device says about itself - the field set discovery advertises and a session, once one
 * exists, can be asked for. [deviceId] and [displayName] are the only parts a handshake actually
 * proves; everything else here is the peer's own unverified claim.
 */
data class PeerDescriptor(
    val deviceId: String,
    val displayName: String,
    val kind: Kind?,
    val accessMode: AccessMode?,
    val advertised: Advertised,
) {
    enum class Kind { Desktop, Laptop, Phone, Tablet, Nas }

    /** How the peer says it gates connections. A hint for the UI; the peer enforces it, not us. */
    enum class AccessMode { Open, Password, Key }

    /** What the peer announced before anyone connected. Not verified - the handshake does that. */
    data class Advertised(
        val protocolVersions: IntRange? = null,
        val fingerprint: Fingerprint? = null,
        val dictionaryId: String? = null,
        val dictionaryVersion: Int? = null,
    )
}
