package com.fserver.app.data.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import com.fserver.core.network.auth.AuthMethod
import com.fserver.core.network.auth.OfferedAuthMethod
import com.fserver.core.store.AuthSettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * The offered set, persisted so a restart does not silently re-open a method the user closed.
 *
 * `NetworkController` reads `offeredMethods.value` while building the node, which is before the
 * first `DataStore` read lands — so the node starts on [DEFAULT_METHODS] and hot-swaps to the
 * stored set on the first emission. That swap is the same one a settings toggle triggers.
 *
 * TODO: the server password is this device's long-lived secret and sits in plain preferences.
 *  It belongs behind the keystore, next to the identity key pair.
 */
internal class AuthSettingsStoreImpl(
    private val dataStore: DataStore<Preferences>,
    scope: CoroutineScope,
) : AuthSettingsStore {

    override val offeredMethods: StateFlow<List<OfferedAuthMethod>> = dataStore.data
        .map { it.toOfferedMethods() }
        .stateIn(scope, SharingStarted.Eagerly, DEFAULT_METHODS.toOfferedMethods(DEFAULT_PASSWORD))

    override suspend fun setEnabled(method: AuthMethod, enabled: Boolean) {
        dataStore.edit { prefs ->
            val current = prefs[ENABLED] ?: DEFAULT_METHODS
            prefs[ENABLED] = if (enabled) current + method.name else current - method.name
        }
    }

    override suspend fun setServerPassword(password: String) {
        dataStore.edit { prefs -> prefs[PASSWORD] = password }
    }

    private fun Preferences.toOfferedMethods(): List<OfferedAuthMethod> =
        (this[ENABLED] ?: DEFAULT_METHODS).toOfferedMethods(this[PASSWORD] ?: DEFAULT_PASSWORD)

    private companion object {
        val ENABLED = stringSetPreferencesKey("auth_offered_methods")
        val PASSWORD = stringPreferencesKey("auth_server_password")

        // TODO: development only — a first run should ask for a password rather than ship one.
        const val DEFAULT_PASSWORD = "ABCD"
        val DEFAULT_METHODS = setOf(AuthMethod.ConfirmFingerprint.name, AuthMethod.Password.name)
    }
}

/** Unknown names are dropped: a downgrade must not fail the whole set. */
private fun Set<String>.toOfferedMethods(password: String): List<OfferedAuthMethod> =
    listOfNotNull(
        OfferedAuthMethod.ConfirmFingerprint.takeIf { AuthMethod.ConfirmFingerprint.name in this },
        OfferedAuthMethod.Password(password).takeIf { AuthMethod.Password.name in this },
    )
