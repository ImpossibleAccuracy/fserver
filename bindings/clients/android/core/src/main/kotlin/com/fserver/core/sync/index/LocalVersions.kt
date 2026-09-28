package com.fserver.core.sync.index

import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.version.HlcTimestamp
import com.fserver.core.sync.version.HybridLogicalClock
import com.fserver.core.sync.version.VersionVector

/** Where this device's own versions come from. */
internal class LocalVersions(
    private val storage: FServerStorage,
    private val clock: HybridLogicalClock,
) {
    /** Issues [count] new versions, each stamped with its own HLC reading. */
    suspend fun issuer(count: Int) = VersionIssuer(
        deviceId = storage.identity.localDevice().deviceId,
        stamps = clock.ticks(count).iterator(),
    )
}

internal class VersionIssuer(
    private val deviceId: String,
    private val stamps: Iterator<HlcTimestamp>,
) {
    fun after(previous: LocalIndexedFile.Version?) = LocalIndexedFile.Version(
        vector = (previous?.vector ?: VersionVector.Empty).bump(deviceId),
        hlc = stamps.next(),
        originDevice = deviceId,
    )
}
