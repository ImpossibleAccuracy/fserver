package com.fserver.core.store

import com.fserver.core.network.auth.AuthMethod
import com.fserver.core.network.auth.OfferedAuthMethod
import kotlinx.coroutines.flow.StateFlow

/** How this device authenticates peers dialing in. */
interface AuthSettingsStore {
    val offeredMethods: StateFlow<List<OfferedAuthMethod>>

    /**
     * Turns [method] on or off, keeping whatever secret is already stored for it.
     *
     * `NetworkController` watches [offeredMethods] and hot-swaps the node's methods on the next
     * emission, so no restart is needed.
     *
     * Switching off the last method leaves no way for a peer to authenticate. That is the caller's
     * call to refuse — only it knows what the user was trying to do.
     */
    suspend fun setEnabled(method: AuthMethod, enabled: Boolean)

    /**
     * Replaces this device's server password — the secret peers prove they know. Stored whether or
     * not [AuthMethod.Password] is currently offered.
     */
    suspend fun setServerPassword(password: String)
}
