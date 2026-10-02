package com.fserver.core.crypto

import com.fserver.core.crypto.model.EncryptionPolicy

/** How a source's files held here stand against its [EncryptionPolicy]. */
sealed interface EncryptionStatus {
    /** Its location is shared storage other apps read: never encrypted. */
    data object Unsupported : EncryptionStatus

    data object Off : EncryptionStatus

    data class Encrypted(val cipherId: String) : EncryptionStatus

    /** [remaining] files still to seal under `Required`, or to open under `Off`. */
    data class Migrating(val remaining: Int, val toward: EncryptionPolicy) : EncryptionStatus
}
