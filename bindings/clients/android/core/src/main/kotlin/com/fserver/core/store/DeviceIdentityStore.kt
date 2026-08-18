package com.fserver.core.store

import com.fserver.core.network.device.model.LocalDevice
import java.security.KeyPair

/** Who this device is, persisted by the host. Renaming is a UI concern and lives on the repository. */
@SubclassOptInRequired(FServerStorageApi::class)
interface DeviceIdentityStore {
    /** Long-term signing key. */
    val identityKeyPair: KeyPair

    /**
     * This device's advertised identity. Read on every handshake, so it must be cheap - cache it.
     *
     * TODO: peers already advertised keep the old name until advertising restarts - `:net` has no
     *  way to re-publish a descriptor in place.
     */
    suspend fun localDevice(): LocalDevice
}
