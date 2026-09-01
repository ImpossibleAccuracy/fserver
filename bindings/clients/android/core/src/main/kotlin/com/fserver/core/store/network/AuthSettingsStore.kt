package com.fserver.core.store.network

import com.fserver.core.network.auth.OfferedAuthMethod
import com.fserver.core.store.FServerStorageApi
import kotlinx.coroutines.flow.StateFlow

/**
 * How this device authenticates peers dialing in.
 *
 * Read-only on purpose: `NetworkController` watches [offeredMethods] and hot-swaps the node's
 * methods on the next emission. Changing the set is a UI action, so the setters live on the
 * repository instead.
 */
@SubclassOptInRequired(FServerStorageApi::class)
interface AuthSettingsStore {
    /**
     * Methods currently offered to peers. Read synchronously while the node is being built, so it
     * must carry a usable value before any async load lands.
     */
    val offeredMethods: StateFlow<List<OfferedAuthMethod>>
}
