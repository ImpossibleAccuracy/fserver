package com.fserver.app.util

import kotlinx.coroutines.flow.stateIn

// Default `combine` operator in Kotlin Flow supports up to 5 flows.

fun <T1, T2, T3, T4, T5, T6, R> combineMany(
    flow1: kotlinx.coroutines.flow.Flow<T1>,
    flow2: kotlinx.coroutines.flow.Flow<T2>,
    flow3: kotlinx.coroutines.flow.Flow<T3>,
    flow4: kotlinx.coroutines.flow.Flow<T4>,
    flow5: kotlinx.coroutines.flow.Flow<T5>,
    flow6: kotlinx.coroutines.flow.Flow<T6>,
    transform: suspend (T1, T2, T3, T4, T5, T6) -> R
): kotlinx.coroutines.flow.Flow<R> = kotlinx.coroutines.flow.combine(
    flow1,
    flow2,
    flow3,
    flow4,
    flow5,
    flow6,
) { arr ->
    @Suppress("UNCHECKED_CAST")
    transform(
        arr[0] as T1,
        arr[1] as T2,
        arr[2] as T3,
        arr[3] as T4,
        arr[4] as T5,
        arr[5] as T6
    )
}

fun <T1, T2, T3, T4, T5, T6, T7, R> combineMany(
    flow1: kotlinx.coroutines.flow.Flow<T1>,
    flow2: kotlinx.coroutines.flow.Flow<T2>,
    flow3: kotlinx.coroutines.flow.Flow<T3>,
    flow4: kotlinx.coroutines.flow.Flow<T4>,
    flow5: kotlinx.coroutines.flow.Flow<T5>,
    flow6: kotlinx.coroutines.flow.Flow<T6>,
    flow7: kotlinx.coroutines.flow.Flow<T7>,
    transform: suspend (T1, T2, T3, T4, T5, T6, T7) -> R
): kotlinx.coroutines.flow.Flow<R> = kotlinx.coroutines.flow.combine(
    flow1,
    flow2,
    flow3,
    flow4,
    flow5,
    flow6,
    flow7,
) { arr ->
    @Suppress("UNCHECKED_CAST")
    transform(
        arr[0] as T1,
        arr[1] as T2,
        arr[2] as T3,
        arr[3] as T4,
        arr[4] as T5,
        arr[5] as T6,
        arr[6] as T7
    )
}

/** A screen's state: kept 5 s past the last subscriber, so a rotation does not restart it. */
fun <T> kotlinx.coroutines.flow.Flow<T>.stateInScreen(
    scope: kotlinx.coroutines.CoroutineScope,
    initialValue: T,
): kotlinx.coroutines.flow.StateFlow<T> = stateIn(
    scope,
    kotlinx.coroutines.flow.SharingStarted.WhileSubscribed(5_000),
    initialValue,
)
