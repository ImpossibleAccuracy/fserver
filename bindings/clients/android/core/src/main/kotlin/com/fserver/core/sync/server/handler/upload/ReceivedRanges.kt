package com.fserver.core.sync.server.handler.upload

import java.util.TreeMap

/** Byte ranges written so far, merged as they touch. Not thread-safe. */
internal class ReceivedRanges(prefix: Long = 0) {
    /** Start -> end, exclusive. Never two that overlap or touch. */
    private val ranges = TreeMap<Long, Long>()

    init {
        if (prefix > 0) ranges[0] = prefix
    }

    /** End of the run starting at 0: everything below it is on disk. */
    val prefix: Long
        get() = ranges[0] ?: 0

    /** Bytes held, gaps not counted. */
    val total: Long
        get() = ranges.entries.sumOf { it.value - it.key }

    /** Does any range cover [start, end)? */
    fun covers(start: Long, end: Long): Boolean {
        val range = ranges.floorEntry(start) ?: return false
        return range.value >= end
    }

    /** Add a range, merging with any that touch. */
    fun add(start: Long, end: Long) {
        if (end <= start) return

        var from = start
        var to = end

        ranges.floorEntry(start)?.takeIf { it.value >= start }?.let {
            from = it.key
            to = maxOf(to, it.value)
            ranges.remove(it.key)
        }

        while (true) {
            val next = ranges.ceilingEntry(from)?.takeIf { it.key <= to } ?: break
            to = maxOf(to, next.value)
            ranges.remove(next.key)
        }

        ranges[from] = to
    }
}
