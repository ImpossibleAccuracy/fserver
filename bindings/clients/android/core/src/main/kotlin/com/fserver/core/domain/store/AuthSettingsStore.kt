package com.fserver.core.domain.store

import com.fserver.core.domain.model.connection.auth.OfferedAuthMethod
import kotlinx.coroutines.flow.StateFlow

/** How this device authenticates peers dialing in. */
interface AuthSettingsStore {
    val offeredMethods: StateFlow<List<OfferedAuthMethod>>
}
