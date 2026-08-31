package com.fserver.core.files.source

/**
 * Content digest of a file.
 *
 * [algorithm] is stored rather than assumed: a digest whose algorithm is implicit cannot be
 * migrated, and every record written before a switch would silently compare unequal afterwards.
 */
data class FileHash(
    val algorithm: Algorithm,
    /** Lowercase hex, no separators. */
    val hex: String,
) {
    init {
        require(hex.isNotBlank()) { "Hash cannot be blank" }
    }

    override fun toString(): String = "${algorithm.name.lowercase()}:$hex"

    enum class Algorithm {
        /** The only one written today. Kept as an enum so adding a second is not a migration. */
        Sha256,
    }
}
