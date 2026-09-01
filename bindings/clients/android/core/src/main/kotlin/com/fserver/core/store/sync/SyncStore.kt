package com.fserver.core.store.sync

import com.fserver.core.sync.SyncPreferences
import com.fserver.core.store.FServerStorageApi

/** The sync settings shared by every source - see [SyncPreferences]. */
@SubclassOptInRequired(FServerStorageApi::class)
interface SyncStore {
    suspend fun getSourceRules(): SyncPreferences

    suspend fun saveSourceRules(rules: SyncPreferences)
}
