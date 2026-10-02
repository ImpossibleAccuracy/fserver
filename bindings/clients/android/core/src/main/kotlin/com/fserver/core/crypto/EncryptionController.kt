package com.fserver.core.crypto

import com.fserver.core.crypto.internal.SealedFiles
import com.fserver.core.crypto.model.AtRest
import com.fserver.core.crypto.model.EncryptionPolicy
import com.fserver.core.crypto.model.supportsEncryption
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.model.SourceEntry
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** At-rest encryption as a host shows it. The policy itself is set through `SourcesController`. */
class EncryptionController internal constructor(
    private val storage: FServerStorage,
    private val sealedFiles: SealedFiles,
) {
    /** What `EncryptionPolicy.Required` may name here: the built-in cipher first, then the host's. */
    val cipherIds: List<String> get() = sealedFiles.cipherIds

    /**
     * Where [source]'s files held here stand. Follows the index, not the source: pass the source
     * again when its policy changes.
     */
    fun status(source: SourceEntry): Flow<EncryptionStatus> {
        if (!source.location.supportsEncryption) return flowOf(EncryptionStatus.Unsupported)

        val policy = source.preferences.encryption
        return storage.index.all
            .map { rows -> rows.count { it.sourceId == source.id && it.state is LocalIndexedFile.State.Present && !it.atRest.meets(policy) } }
            .distinctUntilChanged()
            .map { remaining ->
                when {
                    remaining > 0 -> EncryptionStatus.Migrating(remaining, policy)
                    policy is EncryptionPolicy.Required -> EncryptionStatus.Encrypted(policy.cipherId)
                    else -> EncryptionStatus.Off
                }
            }
    }
}

private fun AtRest.meets(policy: EncryptionPolicy): Boolean = when (policy) {
    EncryptionPolicy.Off -> this == AtRest.Plain
    is EncryptionPolicy.Required -> this is AtRest.Sealed && cipherId == policy.cipherId
}
