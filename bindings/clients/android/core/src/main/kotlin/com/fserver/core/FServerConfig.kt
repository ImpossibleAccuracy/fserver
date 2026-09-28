package com.fserver.core

import android.content.Context
import com.fserver.core.files.preview.EvictionPreviewer
import com.fserver.core.util.DefaultTimeProvider
import com.fserver.core.util.TimeProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Everything [FServerCore] needs from its host.
 *
 * @param context application context.
 * @param backgroundScope scope for background work.
 * @param timeProvider provides the current time.
 * @param evictionPreviewer keeps a preview of a file before eviction frees its bytes; null keeps none.
 */
data class FServerConfig(
    val context: Context,
    val backgroundScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
    val timeProvider: TimeProvider = DefaultTimeProvider,
    val evictionPreviewer: EvictionPreviewer? = null,
)
