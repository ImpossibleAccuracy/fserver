package com.fserver.core

import android.content.Context
import com.fserver.core.store.FServerStorage
import kotlinx.coroutines.CoroutineScope

/**
 * Everything [FServerCore] needs from its host.
 *
 * @param context application context.
 * @param storage unified storage for all FServer data.
 * @param backgroundScope scope for background work.
 * `null` means the core creates and owns one, and cancels it on [FServerCore.close].
 */
data class FServerConfig(
    val context: Context,
    val backgroundScope: CoroutineScope? = null,
    val storage: FServerStorage,
)
