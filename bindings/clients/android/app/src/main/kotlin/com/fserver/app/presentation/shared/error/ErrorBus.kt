package com.fserver.app.presentation.shared.error

import com.fserver.core.requirement.RequirementReport
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import timber.log.Timber
import kotlin.coroutines.cancellation.CancellationException

/**
 * Where anything that failed goes.
 *
 * Mixed into a view model by delegation (`ViewModel(), ErrorReporter by reporter`) so a failure
 * with nowhere to sit in the screen's own state still reaches the user instead of stopping at a
 * `Timber.w`. Only to write half is injected into screens: nothing below the root reads the
 * stream back.
 */
interface ErrorReporter {

    /** [context] is for the log only — what the user sees comes from [toAppError]. */
    fun report(error: Throwable, context: String? = null)

    fun report(error: AppError)

    /** Reports an unmet report as a failure; does nothing when there is nothing in the way. */
    fun report(requirements: RequirementReport)
}

/**
 * The single app-wide stream of them, read by `AppViewModel` and shown over whatever screen is
 * open.
 *
 * One bus rather than one per screen: a failure can outlive the screen that caused it — a pass
 * that breaks in the background, a request answered from a sheet that closes on the tap — and a
 * per-screen channel drops those on the floor.
 */
interface ErrorBus : ErrorReporter {
    val errors: Flow<AppError>
}

fun ErrorBus(): ErrorBus = ChannelErrorBus()

private class ChannelErrorBus : ErrorBus {
    // Buffered rather than conflated: two failures in a pass are two things the user should see.
    private val channel = Channel<AppError>(Channel.BUFFERED)

    override val errors: Flow<AppError> = channel.receiveAsFlow()

    override fun report(error: Throwable, context: String?) {
        // A canceled job is the screen going away, not a failure to explain.
        if (error is CancellationException) return

        Timber.w(error, context ?: "reported to the user")
        report(error.toAppError())
    }

    override fun report(error: AppError) {
        channel.trySend(error)
    }

    override fun report(requirements: RequirementReport) {
        if (requirements.isSatisfied) return
        report(requirements.toAppError())
    }
}
