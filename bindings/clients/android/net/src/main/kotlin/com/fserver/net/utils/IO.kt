package com.fserver.net.utils

import kotlinx.coroutines.CancellationException

/** [runCatching] that still lets structured cancellation through. */
internal inline fun <T> netRunCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}