package com.fserver.common.utils

/**
 * Chains two [Result]s together, returning the first one if it is success, or the second one if the first is failure.
 *
 * @return [Result] of the first success, or the last failure if both are failures.
 */
inline fun <T> Result<T>?.chainWith(other: () -> Result<T>?): Result<T>? {
    if (this != null && isSuccess) return this

    return other() ?: this
}

inline fun <T> Result<T>.chainWith(other: () -> Result<T>?): Result<T> {
    if (isSuccess) return this

    return other() ?: this
}
