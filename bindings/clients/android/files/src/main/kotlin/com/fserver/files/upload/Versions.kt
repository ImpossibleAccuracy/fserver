package com.fserver.files.upload

/** One version of a file. Only [vector] orders versions; the rest breaks ties between concurrent ones. */
data class FileVersion(
    val vector: VersionVector,
    /** Packed HLC reading of when the version was made. Orders as a plain integer. */
    val hlc: Long,
    /** Device that made the version. */
    val originDevice: String,
) {
    /** Version covering both histories, stamped like the latter of the two so all sides agree. */
    fun merge(other: FileVersion?): FileVersion {
        if (other == null) return this
        val later = maxOf(this, other, HlcComparator)
        return later.copy(vector = vector.merge(other.vector))
    }

    fun compareHlc(other: FileVersion): Boolean {
        return HlcComparator.compare(this, other) >= 0
    }

    companion object {
        val HlcComparator = compareBy<FileVersion>({ it.hlc }, { it.originDevice })
    }
}

/** How many times each device has edited one file. Orders versions without trusting any clock. */
data class VersionVector(val counters: Map<String, Long> = emptyMap()) {
    operator fun get(deviceId: String): Long = counters[deviceId] ?: 0

    /** Everything either side has seen. */
    fun merge(other: VersionVector): VersionVector =
        VersionVector(
            (counters.keys + other.counters.keys)
                .associateWith { maxOf(this[it], other[it]) })

    /** Where this version stands relative to [other]. */
    fun compare(other: VersionVector): Causality {
        val devices = counters.keys + other.counters.keys
        val seesAll = devices.all { this[it] >= other[it] }
        val seenByOther = devices.all { this[it] <= other[it] }

        return when {
            seesAll && seenByOther -> Causality.Equal
            seesAll -> Causality.Newer
            seenByOther -> Causality.Older
            else -> Causality.Concurrent
        }
    }

    companion object {
        val Empty = VersionVector()
    }
}

/** Result of [VersionVector.compare], read from the receiver's side. */
enum class Causality {
    Equal,

    /** Receiver already includes every edit of the other version, plus some. */
    Newer,

    /** The other version includes every edit of the receiver, plus some. */
    Older,

    /** Each side has an edit the other has not seen: a real conflict. */
    Concurrent,
}
