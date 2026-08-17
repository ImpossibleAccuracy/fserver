package com.fserver.app.data.storage

import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fserver.app.data.AndroidKeyPairStore
import com.fserver.core.network.device.model.DeviceKind
import com.fserver.core.network.device.model.LocalDevice
import com.fserver.core.store.DeviceIdentityStore
import com.fserver.core.store.util.Cached
import java.security.KeyPair
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal class DeviceIdentityStoreImpl(
    private val dataStore: DataStore<Preferences>,
) : DeviceIdentityStore {
    private val keyPairStore = AndroidKeyPairStore()

    override val identityKeyPair: KeyPair
        get() = keyPairStore.getOrCreate(KEY_ALIAS)

    override val localDevice: Cached<LocalDevice> = Cached {
        val prefs = dataStore.edit { prefs ->
            if (prefs[DEVICE_ID] == null) prefs[DEVICE_ID] = Uuid.generateV7().toString()
        }

        LocalDevice(
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
