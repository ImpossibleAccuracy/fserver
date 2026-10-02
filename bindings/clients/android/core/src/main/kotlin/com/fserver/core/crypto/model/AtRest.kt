package com.fserver.core.crypto.model

/**
 * How a file's bytes sit on disk. A file whose state disagrees with its source's
 * [EncryptionPolicy] is pending migration.
 */
sealed interface AtRest {
    data object Plain : AtRest

    data class Sealed(val cipherId: String, val keyId: String) : AtRest
}
