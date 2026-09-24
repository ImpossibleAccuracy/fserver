package com.fserver.core.sync.version

/**
 * How many times each device has edited one file. Orders versions without trusting any clock; the
 * ordering itself belongs to the strategy, which sees `:files`' mirror of this type.
 *
 * Zero counters are never stored, so equal histories are equal values.
 */
data class VersionVector(val counters: Map<String, Long> = emptyMap()) {
    init {
        require(counters.values.all { it > 0 }) { "Counters must be positive: $counters" }
    }

    /** The vector after [deviceId] makes one more edit. */
    fun bump(deviceId: String): VersionVector =
        VersionVector(counters + (deviceId to (counters[deviceId] ?: 0) + 1))

    companion object {
        val Empty = VersionVector()
    }
}
