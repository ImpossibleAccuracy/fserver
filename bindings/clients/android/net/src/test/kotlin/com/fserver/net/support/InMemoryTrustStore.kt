package com.fserver.net.support

import com.fserver.net.security.trust.PeerTrustStore
import com.fserver.net.security.trust.TrustRecord

/** What a host's persistent store does, minus the persistence. */
class InMemoryTrustStore(
    /** Blows up on write, to check a pin failure costs a prompt and not the session. */
    private val failOnPin: Boolean = false,
) : PeerTrustStore {
    private val records = mutableListOf<TrustRecord>()

    val pinned: List<TrustRecord> get() = records.toList()

    override suspend fun find(publicKey: ByteArray): TrustRecord? =
        records.firstOrNull { it.publicKey.contentEquals(publicKey) }

    override suspend fun findByDeviceId(deviceId: String): List<TrustRecord> =
        records.filter { it.deviceId == deviceId }

    override suspend fun pin(record: TrustRecord) {
        if (failOnPin) error("store is down")
        records.removeAll { it.publicKey.contentEquals(record.publicKey) }
        records += record
    }
}
