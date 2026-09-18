package com.fserver.core.network.dictionary

import com.fserver.core.sync.progress.SyncFailureReason
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * `ReleaseLease` on the wire, which is where a pass tells the other device how it went.
 *
 * The field was added after the dictionary shipped, so both directions of the version skew have to
 * survive it: an older peer that sends nothing, and a newer one that sends a reason this build has
 * never heard of. Either one failing the message would strand the lease until its TTL.
 */
class ReleaseLeaseCodecTest {

    private val codec = FileServerDictionary().codec

    @Test
    fun `a reason survives the round trip`() {
        val message = FileServerMessages.AcquireSyncLease.ReleaseLease(
            sourceId = SourceId,
            leaseId = LeaseId,
            failure = SyncFailureReason.SourceUnavailable,
        )

        assertEquals(message, codec.decode(codec.encode(message)))
    }

    @Test
    fun `a pass that finished clean says nothing`() {
        val message = FileServerMessages.AcquireSyncLease.ReleaseLease(SourceId, LeaseId)

        val decoded = codec.decode(codec.encode(message))
            as FileServerMessages.AcquireSyncLease.ReleaseLease

        assertNull(decoded.failure)
    }

    @Test
    fun `an older peer that sends no reason is read as a clean pass`() {
        val decoded = codec.decode(releaseLeaseJson(failure = null))
            as FileServerMessages.AcquireSyncLease.ReleaseLease

        assertEquals(SourceId, decoded.sourceId)
        assertNull(decoded.failure)
    }

    @Test
    fun `a reason this build does not know is dropped, not thrown on`() {
        val decoded = codec.decode(releaseLeaseJson(failure = "SomethingInventedLater"))
            as FileServerMessages.AcquireSyncLease.ReleaseLease

        // Coarser than the peer meant, but the message still lands and the lease is still freed.
        assertEquals(LeaseId, decoded.leaseId)
        assertNull(decoded.failure)
    }

    /** Written by hand, because the point is a payload this build would not have produced. */
    private fun releaseLeaseJson(failure: String?): ByteArray {
        val reason = failure?.let { ""","failure":"$it"""" }.orEmpty()

        return """
            {"type":"$ReleaseLeaseType","sourceId":"$SourceId","leaseId":"$LeaseId"$reason}
        """.trimIndent().encodeToByteArray()
    }

    private companion object {
        const val SourceId = "source-1"
        const val LeaseId = "lease-1"

        /** The discriminator kotlinx writes for the sealed hierarchy. */
        const val ReleaseLeaseType =
            "com.fserver.core.network.dictionary.FileServerMessages.AcquireSyncLease.ReleaseLease"
    }
}
