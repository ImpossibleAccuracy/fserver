package com.fserver.core.crypto

import com.fserver.core.crypto.internal.SealedFiles
import com.fserver.core.crypto.model.AtRest
import com.fserver.core.crypto.model.EncryptionPolicy
import com.fserver.core.files.SourceLocation
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.indexedFile
import com.fserver.core.support.sourceEntry
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.model.SourceEntry
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Instant

/** Status counts only files held here, against what the source's policy wants of them. */
class EncryptionControllerTest {
    private val storage = FakeStorage()
    private val controller = EncryptionController(storage, SealedFiles(emptyList(), storage.storageKeys))
    private val sealed = AtRest.Sealed(EncryptionPolicy.BuiltInCipherId, "key-1")

    @Test
    fun `shared storage is never encrypted`() = runTest {
        assertEquals(EncryptionStatus.Unsupported, controller.status(source(SourceLocation.Media, EncryptionPolicy.Off)).first())
    }

    @Test
    fun `files left to seal show as migrating, then as encrypted`() = runTest {
        val source = source(policy = EncryptionPolicy.Required())
        rows(AtRest.Plain, sealed, AtRest.Plain)
        assertEquals(EncryptionStatus.Migrating(2, EncryptionPolicy.Required()), controller.status(source).first())

        rows(sealed, sealed, sealed)
        assertEquals(EncryptionStatus.Encrypted(EncryptionPolicy.BuiltInCipherId), controller.status(source).first())
    }

    @Test
    fun `sealed files under Off are left to open, and evicted ones do not count`() = runTest {
        val source = source(policy = EncryptionPolicy.Off)
        rows(sealed, AtRest.Plain)
        storage.index.markProcessed(
            listOf(indexedFile(id = "evicted", fileId = "evicted", state = LocalIndexedFile.State.Evicted(Instant.DISTANT_PAST)).copy(atRest = sealed))
        )

        assertEquals(EncryptionStatus.Migrating(1, EncryptionPolicy.Off), controller.status(source).first())
    }

    private suspend fun rows(vararg atRest: AtRest) = storage.index.markProcessed(
        atRest.mapIndexed { i, it -> indexedFile(id = "row-$i", fileId = "file-$i").copy(atRest = it) }
    )

    private fun source(location: SourceLocation.Persistable = SourceLocation.Internal("bucket"), policy: EncryptionPolicy): SourceEntry {
        val base = sourceEntry(location = location)
        return base.copy(preferences = base.preferences.copy(encryption = policy))
    }
}
