package com.fserver.app.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Preferences the client owns, as opposed to the ones `:core` enforces.
 *
 * Nothing here is a security control — the server decides what it accepts. [discoverable] gates
 * advertising, which is a real effect.
 */
class AppSettingsStore(
    private val dataStore: DataStore<Preferences>,
) {
    /** Whether the phone advertises itself, so trusted machines can find it. */
    val discoverable: Flow<Boolean> = flag(DISCOVERABLE, default = true)

    /** Whether the phone looks for trusted devices on its own, so sync can start without a tap. */
    val discoveryEnabled: Flow<Boolean> = flag(DISCOVERY, default = true)

    suspend fun setDiscoverable(enabled: Boolean) = set(DISCOVERABLE, enabled)

    suspend fun setDiscoveryEnabled(enabled: Boolean) = set(DISCOVERY, enabled)

    private fun flag(key: Preferences.Key<Boolean>, default: Boolean): Flow<Boolean> =
        dataStore.data.map { it[key] ?: default }

    private suspend fun set(key: Preferences.Key<Boolean>, value: Boolean) {
        dataStore.edit { prefs -> prefs[key] = value }
    }

    private companion object {
        val DISCOVERABLE = booleanPreferencesKey("net_discoverable")
        val DISCOVERY = booleanPreferencesKey("net_discovery")
    }
}
