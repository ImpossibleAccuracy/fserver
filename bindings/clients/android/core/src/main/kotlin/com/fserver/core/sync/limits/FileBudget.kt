package com.fserver.core.sync.limits

import com.fserver.core.sync.model.SourceEntry

/**
 * Room this device's [limits] leave for files it receives, starting from what it already holds.
 *
 * A held file's update is capped by its growth only, or a file sent small could grow past the limit
 * later. Limits are the receiver's alone - what a device sends is the peer's call.
 */
internal class FileBudget(
    private val limits: SourceEntry.Preferences.FileLimits,
    held: SourceUsage,
) {
    private var files = held.files
    private var bytes = held.bytes

    /** Books a file this side does not hold yet; false, and nothing booked, when it does not fit. */
    fun admitNew(size: Long): Boolean {
        val countFits = limits.maxFiles?.let { files < it } ?: true
        val sizeFits = limits.maxTotalSize?.let { bytes + size <= it.bytes } ?: true
        if (!countFits || !sizeFits) return false

        files++
        bytes += size
        return true
    }

    /** Books a held file changing size; false, and nothing booked, when its growth does not fit. */
    fun admitUpdate(from: Long, to: Long): Boolean {
        val growth = to - from
        val sizeFits = growth <= 0 || limits.maxTotalSize?.let { bytes + growth <= it.bytes } ?: true
        if (!sizeFits) return false

        bytes += growth
        return true
    }
}
