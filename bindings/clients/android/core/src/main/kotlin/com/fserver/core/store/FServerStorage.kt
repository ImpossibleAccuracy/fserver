package com.fserver.core.store

/** The storage for the FServer host. */
interface FServerStorage {
    val identity: DeviceIdentityStore

    val auth: AuthSettingsStore

    val trust: TrustedDevicesStore
}
