package com.fserver.common.utils

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

/**
 * Where the wall clock went, by named stage.
 *
 * Instrumentation for transfer throughput: a slow upload is either disk, crypto, socket or waiting,
 * and guessing which costs more than counting. [enabled] switches the whole thing off; while it is
 * on, a stage costs one `nanoTime` pair and an atomic add.
 */
class StageTimer(private val label: String) {
    private val started = System.nanoTime()
    private val stages = ConcurrentHashMap<String, Stage>()
    private val counters = ConcurrentHashMap<String, AtomicLong>()
    private val lastReport = AtomicLong(started)
    private val lastReportBytes = AtomicLong(0)

    inline fun <T> time(stage: String, block: () -> T): T {
        if (!enabled) return block()

        val from = System.nanoTime()
        try {
            return block()
        } finally {
            record(stage, System.nanoTime() - from)
        }
    }

    /** Published for [time]'s inlining; call [time] instead. */
    fun record(stage: String, nanos: Long) {
        if (!enabled) return

        val entry = stages.getOrPut(stage) { Stage() }
        entry.nanos.addAndGet(nanos)
        entry.count.incrementAndGet()
    }

    /** A number worth reporting next to the timings - bytes moved, chunks sent, queue depth. */
    fun count(name: String, amount: Long = 1) {
        if (!enabled) return

        counters.getOrPut(name) { AtomicLong() }.addAndGet(amount)
    }

    /**
     * [summary], but at most once per [everyMs]; null in between.
     *
     * For the timers that outlive a single file - a session, a socket - where waiting for the end
     * means the numbers arrive after the transfer everyone was watching is over.
     */
    fun periodicSummary(everyMs: Long = ReportPeriodMs): String? {
        if (!enabled) return null

        val now = System.nanoTime()
        val last = lastReport.get()
        val windowMs = (now - last) / 1_000_000
        if (windowMs < everyMs) return null
        if (!lastReport.compareAndSet(last, now)) return null

        // Rate over this window, not since the start: an average taken across the pauses between
        // files says nothing about what the link does while it is actually carrying one.
        val bytes = counters["bytes"]?.get() ?: 0
        val delta = bytes - lastReportBytes.getAndSet(bytes)

        return buildString {
            append(summary())
            if (delta > 0 && windowMs > 0) {
                append(" | window ")
                append("%.2f".format(delta / 1024.0 / 1024.0 / (windowMs / 1000.0)))
                append(" MiB/s")
            }
        }
    }

    /** One line: total, every stage that took time, and every counter. */
    fun summary(): String {
        val totalMs = (System.nanoTime() - started) / 1_000_000.0

        val timings = stages.entries
            .sortedByDescending { it.value.nanos.get() }
            .joinToString(", ") { (stage, value) ->
                "$stage ${value.nanos.get() / 1_000_000.0}ms/${value.count.get()}"
            }

        val amounts = counters.entries
            .sortedBy { it.key }
            .joinToString(", ") { (name, value) -> "$name=${value.get()}" }

        return buildString {
            append("[$label] wall ")
            append("%.1f".format(totalMs))
            append("ms")
            if (timings.isNotEmpty()) append(" | $timings")
            if (amounts.isNotEmpty()) append(" | $amounts")

            val bytes = counters["bytes"]?.get() ?: return@buildString
            if (totalMs <= 0.0) return@buildString
            append(" | ")
            append("%.2f".format(bytes / 1024.0 / 1024.0 / (totalMs / 1000.0)))
            append(" MiB/s")
        }
    }

    private class Stage {
        val nanos = AtomicLong()
        val count = AtomicLong()
    }

    companion object {
        /** Flip to false to take the instrumentation back out of the transfer path. */
        @JvmStatic
        var enabled: Boolean = true

        /** How often a long-lived timer reports itself. */
        const val ReportPeriodMs = 5_000L
    }
}
