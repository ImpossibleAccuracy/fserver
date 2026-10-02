package com.fserver.core.crypto.model

import com.fserver.core.files.SourceLocation

/** Whether a source's files are sealed at rest on this device. Never sent to the peer. */
sealed interface EncryptionPolicy {
    data object Off : EncryptionPolicy

    /**
     * New files are sealed by [cipherId] as they are written; plaintext already there waits for
     * migration. Files sealed by another cipher stay readable while it is registered.
     */
    data class Required(val cipherId: String = BuiltInCipherId) : EncryptionPolicy

    companion object {
        /** AES-256-GCM per segment, always registered. */
        const val BuiltInCipherId = "fserver.aes256gcm-seg.v1"
    }
}

/**
 * Only folders no other app reads may hold ciphertext. Media and Downloads never do; a folder is
 * the user's call that it belongs to FServer alone.
 */
val SourceLocation.supportsEncryption: Boolean
    get() = when (this) {
        is SourceLocation.Internal, is SourceLocation.Tree, is SourceLocation.Directory -> true
        is SourceLocation.Media, is SourceLocation.Downloads, is SourceLocation.Root -> false
    }

/** Refuses [policy] where [location] cannot hold ciphertext. */
internal fun requireEncryptable(location: SourceLocation, policy: EncryptionPolicy) =
    require(policy is EncryptionPolicy.Off || location.supportsEncryption) {
        "$location cannot hold files encrypted at rest"
    }
