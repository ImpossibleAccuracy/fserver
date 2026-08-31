package com.fserver.common.task

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.ProducerScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi


/**
 * A cancellable unit of work that reports progress of type [P] and completes with [R].
 */
interface ProgressTask<P, R> {
    /** Progress updates emitted while the task runs. */
    val progress: Flow<P>

    /** Awaits task completion, wrapping any thrown exception in [Result.failure]. */
    suspend fun result(): Result<R>
}

/**
 * Impl of [ProgressTask] that starts the task immediately and shares the progress flow.
 *
 * @param coroutineScope The scope in which to launch the task.
 * @param task The task to execute, which can emit progress updates via the [ProducerScope].
 */
private class ImmediateProgressTask<P, R>(
    private val coroutineScope: CoroutineScope,
    private val task: suspend ProducerScope<P>.() -> R,
) : ProgressTask<P, R> {
    private val completableResult = CompletableDeferred<Result<R>>()
    override val progress: Flow<P>

    init {
        val coldFlow = channelFlow {
            completableResult.complete(
                // Catch all exceptions into result
                runCatching { task() }
            )
        }.shareIn(
            scope = coroutineScope,
            started = SharingStarted.Eagerly,
        )

        progress = coldFlow
    }

    override suspend fun result(): Result<R> =
        completableResult.await()
}

/**
 * Impl of [ProgressTask] that starts the task lazily when any of [progress] or [result] is first accessed.
 *
 * Unlike [ImmediateProgressTask], [progress] is not shared: only the collector that wins the
 * launch race sees real emissions, a later independent collector gets exceptions from collecting the flow.
 * Safe if [result] is the only caller; collect [progress] separately only before [result] runs.
 *
 * @param task The task to execute, which can emit progress updates via the [ProducerScope].
 */
@OptIn(ExperimentalAtomicApi::class)
private class LazyProgressTask<P, R>(
    private val task: suspend ProducerScope<P>.() -> R,
) : ProgressTask<P, R> {
    private val launched = AtomicBoolean(false)

    private val completableResult = CompletableDeferred<Result<R>>()
    override val progress: Flow<P> = channelFlow {
        if (!launched.compareAndSet(false, true))
            throw IllegalStateException("Task already launched")

        completableResult.complete(
            // Catch all exceptions into result
            runCatching { task() }
        )
    }

    override suspend fun result(): Result<R> {
        if (!launched.load()) {
            // Start the task if it hasn't been started yet
            progress.collect { /* no-op */ }
        }

        return completableResult.await()
    }
}

/**
 * A [ProgressTask] that maps the progress and result of another [ProgressTask].
 *
 * @param delegate The original [ProgressTask] to map.
 * @param progressMapper A function to map the progress values from [P1] to [P2].
 * @param resultMapper A function to map the result value from [R1] to [R2].
 */
private class MappingProgressTask<P1, R1, P2, R2>(
    private val delegate: ProgressTask<P1, R1>,
    private val progressMapper: (P1) -> P2,
    private val resultMapper: (R1) -> R2,
) : ProgressTask<P2, R2> {
    override val progress: Flow<P2>
        get() = delegate.progress.map { progressMapper(it) }

    override suspend fun result(): Result<R2> = delegate.result().map(resultMapper)
}

/** Creates a [ProgressTask] that only starts once [ProgressTask.progress] or [ProgressTask.result] is accessed. */
fun <P, R> progressTask(
    task: suspend ProducerScope<P>.() -> R,
): ProgressTask<P, R> = LazyProgressTask(
    task = task,
)

/** Creates a [ProgressTask] that starts immediately in this [CoroutineScope] and shares its progress. */
fun <P, R> CoroutineScope.progressTask(
    task: suspend ProducerScope<P>.() -> R,
): ProgressTask<P, R> = ImmediateProgressTask(
    coroutineScope = this,
    task = task,
)

fun <P1, R1, P2, R2> ProgressTask<P1, R1>.map(
    progressMapper: (P1) -> P2,
    resultMapper: (R1) -> R2,
): ProgressTask<P2, R2> = MappingProgressTask(
    delegate = this,
    progressMapper = progressMapper,
    resultMapper = resultMapper,
)
