package com.fserver.core

import android.content.Context
import com.fserver.core.store.AuthSettingsStore
import com.fserver.core.store.DeviceIdentityStore
import kotlinx.coroutines.CoroutineScope

/**
 * Everything [FServerCore] needs from its host.
 *
 * @param context application context.
 * @param deviceIdentityStore who this device is, and its signing key. Host-owned because the
 * storage is platform business - Android KeyStore here, something else elsewhere.
 * @param authSettingsStore which methods this device accepts from peers.
 * @param backgroundScope scope for background work.
 * `null` means the core creates and owns one, and cancels it on [FServerCore.close].
 */
data class FServerConfig(
    val context: Context,
    val deviceIdentityStore: DeviceIdentityStore,
    val authSettingsStore: AuthSettingsStore,
    val backgroundScope: CoroutineScope? = null,
)
