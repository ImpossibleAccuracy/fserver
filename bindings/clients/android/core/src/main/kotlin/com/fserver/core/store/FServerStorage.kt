package com.fserver.core.store

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

    /** The last index each source's peer reported. A cache - see [RemoteIndexStore]. */
    val remoteIndex: RemoteIndexStore

    /** The registry of sources the engine syncs. */
    val sources: SourcesStore

    /** Sources a peer asked this device to host, until its user answers. */
    val sourceRequests: SourceRequestsStore

    /** What the user chose for held conflicts, until a pass carries it out. */
    val conflictDecisions: ConflictDecisionsStore

    /** Uploads a peer has not finished pushing here. */
    val uploads: UploadStagingStore

    /** Sync settings shared by every source. */
    val preferences: SyncStore

    /** One-shot transfers, outside any source. */
    val oneShotTransfers: OneShotTransfersStore

    /** Keys files are sealed with at rest. */
    val storageKeys: StorageKeysStore
}
