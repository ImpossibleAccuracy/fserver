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
 * advertising, which is a real effect; the rest describe an app lock this build does not have yet.
 */
class AppSettingsStore(
    private val dataStore: DataStore<Preferences>,
) {
    /** Whether the phone advertises itself, so trusted machines can find it. */
    val discoverable: Flow<Boolean> = flag(DISCOVERABLE, default = true)

    /** Whether the phone looks for trusted devices on its own, so sync can start without a tap. */
    val discoveryEnabled: Flow<Boolean> = flag(DISCOVERY, default = true)

    /**
     * TODO: nothing asks for the PIN yet — the launch-time lock screen is not built, and the PIN
     *  itself is never stored. This flag only drives what the settings screen shows.
     */
    val pinEnabled: Flow<Boolean> = flag(PIN_ENABLED, default = false)

    /** TODO: needs `androidx.biometric`; today it is remembered and never checked. */
    val biometricUnlock: Flow<Boolean> = flag(BIOMETRIC, default = false)

    suspend fun setDiscoverable(enabled: Boolean) = set(DISCOVERABLE, enabled)

    suspend fun setDiscoveryEnabled(enabled: Boolean) = set(DISCOVERY, enabled)

    suspend fun setPinEnabled(enabled: Boolean) = set(PIN_ENABLED, enabled)

    /** Turning the PIN off takes biometric unlock with it: it has nothing left to stand in for. */
    suspend fun setBiometricUnlock(enabled: Boolean) = set(BIOMETRIC, enabled)

    private fun flag(key: Preferences.Key<Boolean>, default: Boolean): Flow<Boolean> =
        dataStore.data.map { it[key] ?: default }

    private suspend fun set(key: Preferences.Key<Boolean>, value: Boolean) {
        dataStore.edit { prefs ->
            prefs[key] = value
            if (key == PIN_ENABLED && !value) prefs[BIOMETRIC] = false
        }
    }

    private companion object {
        val DISCOVERABLE = booleanPreferencesKey("net_discoverable")
        val DISCOVERY = booleanPreferencesKey("net_discovery")
        val PIN_ENABLED = booleanPreferencesKey("lock_pin_enabled")
        val BIOMETRIC = booleanPreferencesKey("lock_biometric")
    }
}
