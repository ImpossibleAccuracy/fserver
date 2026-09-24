package com.fserver.net.connection.impl

import kotlinx.coroutines.ExperimentalForInheritanceCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * [source] mapped through [transform] with no coroutine in between, so [value] is never behind
 * [source] - unlike `map().stateIn()`, which lags by however long its collector takes to run.
 */
@OptIn(ExperimentalForInheritanceCoroutinesApi::class)
internal class DerivedStateFlow<T, R>(
    private val source: StateFlow<T>,
    private val transform: (T) -> R,
) : StateFlow<R> {
    override val value: R get() = transform(source.value)

    override val replayCache: List<R> get() = listOf(value)

    override suspend fun collect(collector: FlowCollector<R>): Nothing {
        source.map(transform).distinctUntilChanged().collect(collector)
        awaitCancellation()
    }
}

/**
 * Create a [StateFlow] derived from another [StateFlow] by applying a transformation function.
 *
 * @param transform The transformation function to apply to the values of the source [StateFlow].
 * @return A new [StateFlow] that emits transformed values based on the source [StateFlow].
 */
fun <T, R> StateFlow<T>.derived(transform: (T) -> R): StateFlow<R> =
    DerivedStateFlow(this, transform)
