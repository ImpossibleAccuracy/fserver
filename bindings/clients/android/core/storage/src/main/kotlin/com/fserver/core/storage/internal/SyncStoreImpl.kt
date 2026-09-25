package com.fserver.core.storage.internal

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import com.fserver.core.store.sync.SyncStore
import com.fserver.core.sync.version.HlcTimestamp
import kotlinx.coroutines.flow.first

internal class SyncStoreImpl(
    private val dataStore: DataStore<Preferences>,
) : SyncStore {

    override suspend fun loadClock(): HlcTimestamp? =
        dataStore.data.first()[CLOCK]?.let(::HlcTimestamp)

    override suspend fun saveClock(timestamp: HlcTimestamp) {
        dataStore.edit { prefs -> prefs[CLOCK] = timestamp.packed }
    }

    private companion object {
        val CLOCK = longPreferencesKey("sync_hlc")
    }
}
