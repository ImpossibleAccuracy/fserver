package com.fserver.core.storage

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.fserver.core.FServerConfig
import com.fserver.core.storage.database.FServerStorageDatabase
import com.fserver.core.storage.internal.AndroidKeystoreWrapper
import com.fserver.core.storage.internal.AuthSettingsStoreImpl
import com.fserver.core.storage.internal.ConflictDecisionsStoreImpl
import com.fserver.core.storage.internal.DeviceIdentityStoreImpl
import com.fserver.core.storage.internal.FileIndexStoreImpl
import com.fserver.core.storage.internal.RemoteIndexStoreImpl
import com.fserver.core.storage.internal.SourceRequestsStoreImpl
import com.fserver.core.storage.internal.SourcesStoreImpl
import com.fserver.core.storage.internal.StorageKeysStoreImpl
import com.fserver.core.storage.internal.SyncStoreImpl
import com.fserver.core.storage.internal.OneShotTransfersStoreImpl
import com.fserver.core.storage.internal.TrustedDevicesStoreImpl
import com.fserver.core.storage.internal.UploadStagingStoreImpl
import com.fserver.core.store.FServerStorage
import com.fserver.core.store.crypto.StorageKeysStore
import com.fserver.core.store.network.AuthSettingsStore
import com.fserver.core.store.network.DeviceIdentityStore
import com.fserver.core.store.network.TrustedDevicesStore
import com.fserver.core.store.sync.ConflictDecisionsStore
import com.fserver.core.store.sync.FileIndexStore
import com.fserver.core.store.sync.RemoteIndexStore
import com.fserver.core.store.sync.SourceRequestsStore
import com.fserver.core.store.sync.SourcesStore
import com.fserver.core.store.sync.SyncStore
import com.fserver.core.store.sync.UploadStagingStore
import com.fserver.core.store.oneshot.OneShotTransfersStore
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
                callback = ForeignKeysEnabled,
            )
        )
    }

    private val identityStore by lazy { DeviceIdentityStoreImpl(dataStore) }
    private val authStore by lazy { AuthSettingsStoreImpl(dataStore, scope) }
    private val trustStore by lazy { TrustedDevicesStoreImpl(database) }
    private val fileIndexStore by lazy { FileIndexStoreImpl(database) }
    private val remoteIndexStore by lazy { RemoteIndexStoreImpl(database) }
    private val sourcesStore by lazy {
        SourcesStoreImpl(database, fileIndexStore, remoteIndexStore, timeProvider)
    }
    private val sourceRequestsStore by lazy { SourceRequestsStoreImpl(database) }
    private val syncStore by lazy { SyncStoreImpl(dataStore) }
    private val uploadStagingStore by lazy { UploadStagingStoreImpl(database) }
    private val conflictDecisionsStore by lazy { ConflictDecisionsStoreImpl(database) }
    private val oneShotTransfersStore by lazy { OneShotTransfersStoreImpl(database) }
    private val storageKeysStore by lazy {
        StorageKeysStoreImpl(database, AndroidKeystoreWrapper(), timeProvider)
    }

    val identity: DeviceIdentityRepository get() = identityStore

    val auth: AuthSettingsRepository get() = authStore

    val trustedDevices: TrustedDevicesRepository get() = trustStore

    val fileSources: RegisteredSourcesRepository get() = sourcesStore

    val oneShotTransfers: OneShotTransfersRepository get() = oneShotTransfersStore

    fun asStorage(): FServerStorage = Storage()

    private inner class Storage : FServerStorage {
        override val identity: DeviceIdentityStore get() = identityStore
        override val auth: AuthSettingsStore get() = authStore
        override val trust: TrustedDevicesStore get() = trustStore
        override val index: FileIndexStore get() = fileIndexStore
        override val remoteIndex: RemoteIndexStore get() = remoteIndexStore
        override val sources: SourcesStore get() = sourcesStore
        override val sourceRequests: SourceRequestsStore get() = sourceRequestsStore
        override val preferences: SyncStore get() = syncStore
        override val uploads: UploadStagingStore get() = uploadStagingStore
        override val conflictDecisions: ConflictDecisionsStore get() = conflictDecisionsStore
        override val oneShotTransfers: OneShotTransfersStore get() = oneShotTransfersStore
        override val storageKeys: StorageKeysStore get() = storageKeysStore
    }

    /**
     * SQLite defaults `foreign_keys` to OFF, per connection and not persisted, so a declared foreign
     * key enforces nothing without this. `onConfigure` is the one safe place for it - the pragma is a
     * no-op inside a transaction, which is where `onCreate` and `onUpgrade` already are.
     */
    private object ForeignKeysEnabled : AndroidSqliteDriver.Callback(FServerStorageDatabase.Schema) {
        override fun onConfigure(db: SupportSQLiteDatabase) {
            super.onConfigure(db)
            db.setForeignKeyConstraintsEnabled(true)
        }
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
