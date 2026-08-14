package com.fserver.net.security.identity

import com.fserver.net.peer.PeerDescriptor
import java.security.SecureRandom
import java.util.UUID


/** Provider for [LocalIdentity] instances. */
interface IdentityStore {
    val local: LocalIdentity
}

/**
 * Identity that lives for one process. Enough to get the handshake running; a real host persists
 * its key pair instead, so a peer that trusted this device once still recognizes it.
 */
class EphemeralIdentityStore(
    deviceId: String = UUID.randomUUID().toString(),
    displayName: String = "unnamed device",
    kind: PeerDescriptor.Kind? = null,
) : IdentityStore {
    override val local: LocalIdentity = LocalIdentity(
        deviceId = deviceId,
        displayName = displayName,
        publicKey = ByteArray(PUBLIC_KEY_SIZE).also(SecureRandom()::nextBytes),
        kind = kind,
    )

    private companion object {
        const val PUBLIC_KEY_SIZE = 32
    }
}
