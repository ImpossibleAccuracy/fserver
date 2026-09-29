package com.fserver.core.sync.limits

import com.fserver.core.sync.model.SourceEntry

/** How many of a source's files this device holds ([com.fserver.core.sync.index.LocalIndexedFile.State.Present]), and their total size. */
data class SourceUsage(
    val files: Int,
    val bytes: Long,
)

/** Share of the tightest of these caps [usage] takes, in percent; null when nothing is capped. */
internal fun SourceEntry.Preferences.FileLimits.usedPercent(usage: SourceUsage): Float? {
    val byCount = maxFiles?.takeIf { it > 0 }?.let { usage.files.toFloat() / it }
    val bySize = maxTotalSize?.bytes?.takeIf { it > 0 }?.let { usage.bytes.toFloat() / it }

    return listOfNotNull(byCount, bySize).maxOrNull()?.times(100)
}
