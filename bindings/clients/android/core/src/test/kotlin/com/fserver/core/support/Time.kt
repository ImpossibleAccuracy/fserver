package com.fserver.core.support

import com.fserver.core.util.TimeProvider
import kotlin.time.Duration
import kotlin.time.Instant

/** A clock the test moves by hand, so nothing under test waits on the wall. */
internal class MutableTimeProvider(
    private var now: Instant = Instant.fromEpochSeconds(1_000_000),
) : TimeProvider {
    override fun now(): Instant = now

    fun advance(by: Duration) {
        now += by
    }
}
