package com.fserver.core.sync.conflict

import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.version.HlcTimestamp
import com.fserver.files.upload.FileRecord

/** Where "keep both" puts this device's copy of a conflicting file. */
internal object ConflictCopies {
    /**
     * `dir/Report.docx` -> `dir/Report (Pixel 8).docx`. [n] above 1 numbers the label, for a name
     * already taken.
     */
    fun path(path: String, deviceLabel: String, n: Int = 1): String {
        val dir = path.substringBeforeLast('/', missingDelimiterValue = "")
        val name = path.substringAfterLast('/')

        // A leading dot names a hidden file, not an extension.
        val dot = name.lastIndexOf('.').takeIf { it > 0 }
        val stem = dot?.let { name.substring(0, it) } ?: name
        val extension = dot?.let { name.substring(it) }.orEmpty()

        val label = deviceLabel.replace('/', '_').replace('\\', '_').trim().ifEmpty { "copy" }
        val suffix = if (n == 1) label else "$label $n"

        val copy = "$stem ($suffix)$extension"
        return if (dir.isEmpty()) copy else "$dir/$copy"
    }
}

internal fun FileRecord.seenVersion(): ConflictDecision.SeenVersion? =
    metadata.version?.let { ConflictDecision.SeenVersion(HlcTimestamp(it.hlc), it.originDevice) }

internal fun LocalIndexedFile.Version.seen(): ConflictDecision.SeenVersion =
    ConflictDecision.SeenVersion(hlc, originDevice)
