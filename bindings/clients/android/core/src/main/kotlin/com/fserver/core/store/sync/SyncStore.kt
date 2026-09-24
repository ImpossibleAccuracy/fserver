package com.fserver.core.store.sync

import com.fserver.core.store.FServerStorageApi
import com.fserver.core.sync.model.SyncPreferences
import com.fserver.core.sync.version.HlcTimestamp

/** The sync settings shared by every source - see [SyncPreferences] - and the engine's clock state. */
@SubclassOptInRequired(FServerStorageApi::class)
interface SyncStore {
    suspend fun getSourceRules(): SyncPreferences

    suspend fun saveSourceRules(rules: SyncPreferences)

    /** Last HLC reading this device issued, or null before the first one. */
    suspend fun loadClock(): HlcTimestamp?

    suspend fun saveClock(timestamp: HlcTimestamp)
}
