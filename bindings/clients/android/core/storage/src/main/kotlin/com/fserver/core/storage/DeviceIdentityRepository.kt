package com.fserver.core.storage

import com.fserver.core.network.device.model.LocalDevice
import kotlinx.coroutines.flow.Flow

/** This device's identity, as a screen needs it: observable, and renameable. */
interface DeviceIdentityRepository {
    val localDevice: Flow<LocalDevice>

    /** Renames this device. [localDevice] re-emits. */
    suspend fun setDisplayName(name: String)
}
