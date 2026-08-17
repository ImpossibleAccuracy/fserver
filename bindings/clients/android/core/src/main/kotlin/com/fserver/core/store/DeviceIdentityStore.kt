package com.fserver.core.store

import com.fserver.core.network.device.model.LocalDevice
import com.fserver.core.store.util.Cached
import java.security.KeyPair

/** Who this device is, persisted by the host. */
interface DeviceIdentityStore {
    /** Long-term signing key. */
    val identityKeyPair: KeyPair

    /** Load info about this device, persisted by the host. */
    val localDevice: Cached<LocalDevice>

    /**
     * Renames this device. [localDevice] reloads on the next read.
     *
     * TODO: peers already advertised to keep the old name until advertising restarts — `:net` has
     *  no way to re-publish a descriptor in place.
     */
    suspend fun setDisplayName(name: String)
}
