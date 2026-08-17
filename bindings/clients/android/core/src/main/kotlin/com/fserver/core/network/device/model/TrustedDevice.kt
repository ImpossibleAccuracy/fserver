package com.fserver.core.network.device.model

import com.fserver.core.network.auth.AuthMethod
import com.fserver.net.security.trust.AuthStrength
import kotlin.time.Instant

/**
 * What a completed handshake leaves behind, so the next one with the same device does not have to
 * put a code in front of the user again.
 */
class TrustedDevice(
    val deviceId: String,
    val displayName: String,
    val publicKey: ByteArray,
    /** The strongest method this key has authenticated with, and what it counts for. */
    val method: AuthMethod,
    val strength: String,
    val lastSeen: Instant,
) {

}
