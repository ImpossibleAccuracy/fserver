package com.fserver.core.storage.internal

import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.version.HlcTimestamp
import com.fserver.core.sync.version.VersionVector

/**
 * How a [LocalIndexedFile.Version] is stored: hlc and originDevice as columns of the file row, the
 * vector as rows of its own table, one per device.
 */
internal object FileVersions {
    /** A row with no hlc has no version, whatever its vector table says. */
    fun read(hlc: Long?, originDevice: String?, counters: Map<String, Long>): LocalIndexedFile.Version? {
        if (hlc == null || originDevice == null) return null

        return LocalIndexedFile.Version(
            vector = VersionVector(counters),
            hlc = HlcTimestamp(hlc),
            originDevice = originDevice,
        )
    }

    /** Vector rows grouped per (sourceId, fileId), for reading many files in two queries. */
    fun <T> group(
        rows: List<T>,
        key: (T) -> Pair<String, String>,
        counter: (T) -> Pair<String, Long>,
    ): Map<Pair<String, String>, Map<String, Long>> =
        rows.groupBy(key).mapValues { (_, entries) -> entries.associate(counter) }
}
