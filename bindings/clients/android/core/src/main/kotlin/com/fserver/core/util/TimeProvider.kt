package com.fserver.core.util

import kotlin.time.Clock
import kotlin.time.Instant

internal interface TimeProvider {
    fun now(): Instant
}

internal object DefaultTimeProvider : TimeProvider {
    override fun now(): Instant = Clock.System.now()
}
