package com.fserver.core.store

import com.fserver.core.network.auth.OfferedAuthMethod
import kotlinx.coroutines.flow.StateFlow

/** How this device authenticates peers dialing in. */
interface AuthSettingsStore {
    val offeredMethods: StateFlow<List<OfferedAuthMethod>>
}
