package com.fserver.app.data.storage

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import com.fserver.app.database.FServerDatabase
import com.fserver.core.store.AuthSettingsStore
import com.fserver.core.store.DeviceIdentityStore
import com.fserver.core.store.FServerStorage
import com.fserver.core.store.TrustedDevicesStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob


/**
 * Impl for [FServerStorage]. Currently, it proxies other storages,
 * but in the future, when app will have its own repos for each storage, it will be a single point of access to them.
 * Each independent storage will be removed/replaced with a repo, and this class will become simple router to them.
 */
internal class ServerGeneralStorage(
    private val dataStore: DataStore<Preferences>,
    private val database: FServerDatabase,
) : FServerStorage {
    /** Storage is built once per process and never torn down, so it owns its own scope. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override val identity: DeviceIdentityStore by lazy {
        DeviceIdentityStoreImpl(dataStore)
    }

    override val auth: AuthSettingsStore by lazy {
        AuthSettingsStoreImpl(dataStore, scope)
    }

    override val trust: TrustedDevicesStore by lazy {
        TrustedDevicesStoreImpl(database)
    }
}
