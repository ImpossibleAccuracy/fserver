package com.fserver.core.store

import com.fserver.core.store.network.AuthSettingsStore
import com.fserver.core.store.network.DeviceIdentityStore
import com.fserver.core.store.network.TrustedDevicesStore
import com.fserver.core.store.sync.FileIndexStore
import com.fserver.core.store.sync.SourcesStore
import com.fserver.core.store.sync.SyncStore

/**
 * Everything the engine persists, handed in by the host.
 *
 * Deliberately assembly-only: it hands out the stores and nothing else. Use
 * `:core:storage` unless you are writing a storage backend - see [FServerStorageApi].
 */
@SubclassOptInRequired(FServerStorageApi::class)
interface FServerStorage {
    val identity: DeviceIdentityStore

    val auth: AuthSettingsStore

    val trust: TrustedDevicesStore

    /** What each source has already worked through. */
    val index: FileIndexStore

    /** The registry of sources the engine syncs. */
    val sources: SourcesStore

    /** Sync settings shared by every source. */
    val preferences: SyncStore
}
