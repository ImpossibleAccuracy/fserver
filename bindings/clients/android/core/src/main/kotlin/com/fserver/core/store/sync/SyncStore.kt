package com.fserver.core.store.sync

import com.fserver.core.store.FServerStorageApi
import com.fserver.core.sync.version.HlcTimestamp

/** The engine's clock state. */
@SubclassOptInRequired(FServerStorageApi::class)
interface SyncStore {
    /** Last HLC reading this device issued, or null before the first one. */
    suspend fun loadClock(): HlcTimestamp?

    suspend fun saveClock(timestamp: HlcTimestamp)
}
