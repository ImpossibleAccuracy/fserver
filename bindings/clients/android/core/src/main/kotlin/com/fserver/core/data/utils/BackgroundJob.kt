package com.fserver.core.data.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal suspend fun <T> runBackgroundJob(block: suspend () -> T): Result<T> = runCatching {
    withContext(Dispatchers.IO) {
        block()
    }
}.onFailure {
    if (it is CancellationException) throw it
}
