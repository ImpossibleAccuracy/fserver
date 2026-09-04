package com.fserver.core.storage.internal

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fserver.core.storage.SyncPreferencesRepository
import com.fserver.core.store.sync.SyncStore
import com.fserver.core.sync.model.SyncPreferences
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class SyncStoreImpl(
    private val dataStore: DataStore<Preferences>,
) : SyncStore, SyncPreferencesRepository {

    override val preferences: Flow<SyncPreferences> = dataStore.data.map { it.toSyncPreferences() }

    override suspend fun getSourceRules(): SyncPreferences = preferences.first()

    override suspend fun saveSourceRules(rules: SyncPreferences) {
        dataStore.edit { prefs ->
            prefs[WIFI_REQUIRED] = rules.deviceConstraints.wifiRequired
            prefs[CHARGING_REQUIRED] = rules.deviceConstraints.chargingRequired
            prefs[CONFLICT_RESOLUTION] = rules.conflictResolution.name
        }
    }

    private fun Preferences.toSyncPreferences() = SyncPreferences(
        deviceConstraints = SyncPreferences.DeviceConstraints(
            wifiRequired = this[WIFI_REQUIRED]
                ?: SyncPreferences.Default.deviceConstraints.wifiRequired,
            chargingRequired = this[CHARGING_REQUIRED]
                ?: SyncPreferences.Default.deviceConstraints.chargingRequired,
        ),
        // An unknown name is a downgrade, not a reason to fail every read.
        conflictResolution = this[CONFLICT_RESOLUTION]
            ?.let { stored ->
                SyncPreferences.ConflictResolution.entries.firstOrNull { it.name == stored }
            }
            ?: SyncPreferences.Default.conflictResolution,
    )

    private companion object {
        val WIFI_REQUIRED = booleanPreferencesKey("sync_wifi_required")
        val CHARGING_REQUIRED = booleanPreferencesKey("sync_charging_required")
        val CONFLICT_RESOLUTION = stringPreferencesKey("sync_conflict_resolution")
    }
}
