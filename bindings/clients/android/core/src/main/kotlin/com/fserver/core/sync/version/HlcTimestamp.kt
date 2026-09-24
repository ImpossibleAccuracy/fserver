package com.fserver.core.sync.version

/**
 * Hybrid logical clock reading: wall-clock milliseconds plus a counter for events within one of them.
 *
 * Packed as 48 bits of millis over 16 bits of counter, so [packed] orders exactly as the timestamp
 * does and can be stored or sent as a plain integer.
 */
@JvmInline
value class HlcTimestamp(val packed: Long) : Comparable<HlcTimestamp> {
    init {
        require(packed >= 0) { "Negative HLC timestamp: $packed" }
    }

    val physicalMs: Long get() = packed ushr LogicalBits

    val logical: Int get() = (packed and MaxLogical.toLong()).toInt()

    override fun compareTo(other: HlcTimestamp): Int = packed.compareTo(other.packed)

    override fun toString(): String = "HlcTimestamp($physicalMs.$logical)"

    companion object {
        private const val LogicalBits = 16
        const val MaxLogical = (1 shl LogicalBits) - 1
        const val MaxPhysicalMs = (1L shl (Long.SIZE_BITS - 1 - LogicalBits)) - 1

        val Zero = HlcTimestamp(0)

        fun of(physicalMs: Long, logical: Int): HlcTimestamp {
            require(physicalMs in 0..MaxPhysicalMs) { "Physical time out of range: $physicalMs" }
            require(logical in 0..MaxLogical) { "Logical counter out of range: $logical" }
            return HlcTimestamp((physicalMs shl LogicalBits) or logical.toLong())
        }
    }
}
