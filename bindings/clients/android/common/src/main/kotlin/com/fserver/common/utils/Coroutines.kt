package com.fserver.common.utils

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * [runCatching] that still lets structured cancellation through.
 *
 * Plain `runCatching` swallows [CancellationException], which turns a canceled parent into a
 * bogus `Result.failure` and leaves the coroutine machinery believing the work completed.
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (t: Throwable) {
    Result.failure(t)
}

/** [runCatchingCancellable] for suspending work, moved onto [Dispatchers.IO]. */
suspend fun <T> runBackgroundJob(block: suspend () -> T): Result<T> = withContext(Dispatchers.IO) {
    runCatchingCancellable { block() }
}

