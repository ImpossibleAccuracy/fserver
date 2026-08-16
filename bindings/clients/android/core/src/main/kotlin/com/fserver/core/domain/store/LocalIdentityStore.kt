package com.fserver.core.domain.store

import com.fserver.core.domain.model.connection.device.LocalDevice
import java.security.KeyPair

/** Who this device is, persisted by the host. */
interface LocalIdentityStore {
    /** Long-term signing key. */
    fun identityKeyPair(): KeyPair

    /**
     * Load info about this device, persisted by the host.
     * The result is cached in memory, so IO operations are allowed here.
     */
    suspend fun localDevice(): LocalDevice
}
