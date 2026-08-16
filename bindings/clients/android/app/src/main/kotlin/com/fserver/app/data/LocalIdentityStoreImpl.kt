package com.fserver.app.data

import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fserver.core.domain.model.connection.device.DeviceKind
import com.fserver.core.domain.model.connection.device.LocalDevice
import com.fserver.core.domain.store.LocalIdentityStore
import java.security.KeyPair
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal class LocalIdentityStoreImpl(
    private val dataStore: DataStore<Preferences>,
) : LocalIdentityStore {
    private val keyPairStore = AndroidKeyPairStore()

    override fun identityKeyPair(): KeyPair = keyPairStore.getOrCreate(KEY_ALIAS)

    override suspend fun localDevice(): LocalDevice {
        val prefs = dataStore.edit { prefs ->
            if (prefs[DEVICE_ID] == null) prefs[DEVICE_ID] = Uuid.generateV7().toString()
        }

        return LocalDevice(
            deviceId = requireNotNull(prefs[DEVICE_ID]),
            displayName = prefs[DEVICE_NAME] ?: Build.MODEL,
            // An unknown value means a rename or a downgrade, not a reason to fail a handshake.
            kind = prefs[DEVICE_KIND]?.let { stored ->
                DeviceKind.entries.firstOrNull { it.name == stored }
            },
        )
    }


    private companion object {
        const val KEY_ALIAS = "fserver.identity"

        val DEVICE_ID = stringPreferencesKey("device_id")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val DEVICE_KIND = stringPreferencesKey("device_kind")
    }
}
