package com.fserver.net.security.trust

/**
 * Where pinned peers live between runs, host-owned.
 */
interface PeerTrustStore {
    /** The record for a proven key, or null when this device has never authenticated it. */
    suspend fun find(publicKey: ByteArray): TrustRecord?

    /**
     * Everything pinned under a claimed device id. More than one means the same id has shown up
     * under different keys, which is what the user has to be shown rather than resolved silently.
     */
    suspend fun findByDeviceId(deviceId: String): List<TrustRecord>

    /**
     * Upsert by [TrustRecord.publicKey], called after every handshake that completes - so this is
     * also where a store stamps its own "last connected".
     *
     * Store [record] as given. Its [TrustRecord.method] and [TrustRecord.strength] are already the
     * strongest known for this key, so the store does not need to check for a downgrade.
     *
     * Persist [AuthStrength] by name; the enum is ordered by policy and may be re-ordered.
     */
    suspend fun pin(record: TrustRecord)
}
