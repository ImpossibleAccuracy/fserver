package com.fserver.core.storage.internal

import android.os.Build
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.fserver.core.network.device.model.DeviceKind
import com.fserver.core.network.device.model.LocalDevice
import com.fserver.core.storage.DeviceIdentityRepository
import com.fserver.core.store.DeviceIdentityStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import java.security.KeyPair
import kotlin.uuid.ExperimentalUuidApi
import kotlin.uuid.Uuid

@OptIn(ExperimentalUuidApi::class)
internal class DeviceIdentityStoreImpl(
    private val dataStore: DataStore<Preferences>,
) : DeviceIdentityStore, DeviceIdentityRepository {
    private val keyPairStore = AndroidKeyPairStore()

    /** Set once the id is known to be on disk, so the common path is a read and not a write. */
    @Volatile
    private var deviceIdWritten = false

    override val identityKeyPair: KeyPair
        get() = keyPairStore.getOrCreate(KEY_ALIAS)

    override val localDevice: Flow<LocalDevice> = flow {
        ensureDeviceId()
        emitAll(dataStore.data.map { it.toLocalDevice() })
    }

    /**
     * `DataStore` keeps the last read in memory, so this is a hot-path-safe read after the first
     * one - which is what the handshake needs.
     */
    override suspend fun localDevice(): LocalDevice = localDevice.first()

    override suspend fun setDisplayName(name: String) {
        dataStore.edit { prefs -> prefs[DEVICE_NAME] = name }
    }

    private suspend fun ensureDeviceId() {
        if (deviceIdWritten) return
        dataStore.edit { prefs ->
            if (prefs[DEVICE_ID] == null) prefs[DEVICE_ID] = Uuid.generateV7().toString()
        }
        deviceIdWritten = true
    }

    private fun Preferences.toLocalDevice() = LocalDevice(
        deviceId = requireNotNull(this[DEVICE_ID]) { "device id missing after ensureDeviceId()" },
        displayName = this[DEVICE_NAME] ?: Build.MODEL,
        // An unknown value means a rename or a downgrade, not a reason to fail a handshake.
        kind = this[DEVICE_KIND]?.let { stored ->
            DeviceKind.entries.firstOrNull { it.name == stored }
        },
    )

    private companion object {
        const val KEY_ALIAS = "fserver.identity"

        val DEVICE_ID = stringPreferencesKey("device_id")
        val DEVICE_NAME = stringPreferencesKey("device_name")
        val DEVICE_KIND = stringPreferencesKey("device_kind")
    }
}
