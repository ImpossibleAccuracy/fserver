package com.fserver.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.fserver.core.FServerConfig
import com.fserver.core.storage.database.FServerStorageDatabase
import com.fserver.core.storage.internal.AuthSettingsStoreImpl
import com.fserver.core.storage.internal.DeviceIdentityStoreImpl
import com.fserver.core.storage.internal.FileIndexStoreImpl
import com.fserver.core.storage.internal.SourceRequestsStoreImpl
import com.fserver.core.storage.internal.SourcesStoreImpl
import com.fserver.core.storage.internal.SyncStoreImpl
import com.fserver.core.storage.internal.TrustedDevicesStoreImpl
import com.fserver.core.store.FServerStorage
import com.fserver.core.store.network.AuthSettingsStore
import com.fserver.core.store.network.DeviceIdentityStore
import com.fserver.core.store.network.TrustedDevicesStore
import com.fserver.core.store.sync.FileIndexStore
import com.fserver.core.store.sync.SourceRequestsStore
import com.fserver.core.store.sync.SourcesStore
import com.fserver.core.store.sync.SyncStore
import com.fserver.core.util.TimeProvider
import kotlinx.coroutines.CoroutineScope

/**
 * The default persistence backend for `:core`, and the only thing a host has to touch.
 *
 * Build one per process. It owns the files it writes - a preferences file and a SQLite database,
 * both namespaced so they cannot collide with the host's own. Hand [coreConfig] to
 * `FServerCore.create`, inject the repositories this class exposes into screens, and never name a
 * `com.fserver.core.store` type: that package is the SPI a backend implements, not an API a UI
 * calls. See `FServerStorageApi`.
 *
 * A host that must control where the bytes live implements [FServerStorage] itself instead of
 * using this class - that is what the SPI is for.
 */
class FServerStorageProvider private constructor(
    private val context: Context,
    private val scope: CoroutineScope,
    private val timeProvider: TimeProvider,
) {
    private val dataStore: DataStore<Preferences> by lazy {
        PreferenceDataStoreFactory.create(scope = scope) {
            context.preferencesDataStoreFile(PREFERENCES_NAME)
        }
    }

    private val database: FServerStorageDatabase by lazy {
        FServerStorageDatabase(
            AndroidSqliteDriver(
                schema = FServerStorageDatabase.Schema,
                context = context,
                name = DATABASE_NAME,
            )
        )
    }

    private val identityStore by lazy { DeviceIdentityStoreImpl(dataStore) }
    private val authStore by lazy { AuthSettingsStoreImpl(dataStore, scope) }
    private val trustStore by lazy { TrustedDevicesStoreImpl(database) }
    private val fileIndexStore by lazy { FileIndexStoreImpl() }
    private val sourcesStore by lazy { SourcesStoreImpl(fileIndexStore, timeProvider) }
    private val sourceRequestsStore by lazy { SourceRequestsStoreImpl() }
    private val syncStore by lazy { SyncStoreImpl(dataStore) }

    val identity: DeviceIdentityRepository get() = identityStore

    val auth: AuthSettingsRepository get() = authStore

    val trustedDevices: TrustedDevicesRepository get() = trustStore

    val fileSources: RegisteredSourcesRepository get() = sourcesStore

    val syncPreferences: SyncPreferencesRepository get() = syncStore

    private inner class Storage : FServerStorage {
        override val identity: DeviceIdentityStore get() = identityStore
        override val auth: AuthSettingsStore get() = authStore
        override val trust: TrustedDevicesStore get() = trustStore
        override val index: FileIndexStore get() = fileIndexStore
        override val sources: SourcesStore get() = sourcesStore
        override val sourceRequests: SourceRequestsStore get() = sourceRequestsStore
        override val preferences: SyncStore get() = syncStore
    }

    companion object {
        private const val PREFERENCES_NAME = "fserver_core"
        private const val DATABASE_NAME = "fserver_core.db"

        fun create(config: FServerConfig) = FServerStorageProvider(
            context = config.context,
            scope = config.backgroundScope,
            timeProvider = config.timeProvider,
        )
    }
}
