package com.fserver.core.store

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

    val fileSources: FileSourcesStore
}
