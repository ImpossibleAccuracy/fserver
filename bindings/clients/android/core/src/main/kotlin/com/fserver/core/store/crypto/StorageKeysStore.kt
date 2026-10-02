package com.fserver.core.store.crypto

import com.fserver.core.crypto.spi.StorageKey
import com.fserver.core.store.FServerStorageApi
import javax.crypto.SecretKey

/**
 * Data keys for files sealed at rest, one per source. Keep them wrapped by a key that never
 * leaves the device (Android Keystore), so a copy of the app's data alone opens nothing.
 */
@SubclassOptInRequired(FServerStorageApi::class)
interface StorageKeysStore {
    /** The key new files of [sourceId] are sealed with, created on first use. */
    suspend fun current(sourceId: String): StorageKey

    /** The key a header names, or null when this device has none. */
    suspend fun resolve(keyId: String): SecretKey?

    /** Drops every key of [sourceId]: whatever was sealed under them is unreadable from then on. */
    suspend fun forget(sourceId: String)
}
