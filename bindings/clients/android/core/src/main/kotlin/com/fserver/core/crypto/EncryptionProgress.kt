package com.fserver.core.crypto

/** A running migration across every source, as one bar. [total] can still grow as sources are rescanned. */
data class EncryptionProgress(
    val done: Int,
    val total: Int,
    val towards: Set<Toward>,
) {
    enum class Toward { Encrypted, Decrypted }

    val fraction: Float
        get() = if (total <= 0) 0f else (done.toFloat() / total).coerceIn(0f, 1f)
}
