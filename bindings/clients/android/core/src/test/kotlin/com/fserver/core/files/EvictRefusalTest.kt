package com.fserver.core.files

import com.fserver.common.model.ContentHash
import com.fserver.common.model.FileSize
import com.fserver.core.support.TestEpoch
import com.fserver.core.support.indexedFile
import com.fserver.core.support.sourceEntry
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.index.RemoteIndexedFile
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import com.fserver.core.sync.model.evictsByHand
import com.fserver.core.sync.version.HlcTimestamp
import com.fserver.core.sync.version.VersionVector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Evicting by hand must never leave a file without a confirmed copy. */
class EvictRefusalTest {

    private val local = indexedFile().copy(hash = Hash)

    @Test
    fun `a file the peer holds byte for byte may be evicted`() {
        assertNull(local.evictRefusal(remote()))
    }

    @Test
    fun `a file missing or not present on the peer is refused`() {
        assertEquals(EvictRefusal.NotOnPeer, local.evictRefusal(remote = null))
        assertEquals(
            EvictRefusal.NotOnPeer,
            local.evictRefusal(remote(state = LocalIndexedFile.State.Evicted(TestEpoch))),
        )
    }

    @Test
    fun `a different version on the peer is refused`() {
        assertEquals(EvictRefusal.PeerDiffers, local.evictRefusal(remote(hash = ContentHash("other", Algorithm))))
    }

    @Test
    fun `unhashed bytes on either side are refused`() {
        assertEquals(EvictRefusal.Unverified, local.copy(hashStale = true).evictRefusal(remote()))
        assertEquals(EvictRefusal.Unverified, local.copy(hash = null).evictRefusal(remote()))
        assertEquals(EvictRefusal.Unverified, local.evictRefusal(remote(hash = null)))
    }

    @Test
    fun `pinned and absent files are refused`() {
        assertEquals(
            EvictRefusal.Pinned,
            local.copy(state = LocalIndexedFile.State.Present(pinned = true)).evictRefusal(remote()),
        )
        assertEquals(
            EvictRefusal.NotHere,
            local.copy(state = LocalIndexedFile.State.Evicted(TestEpoch)).evictRefusal(remote()),
        )
    }

    @Test
    fun `same bytes under histories not merged yet are refused`() {
        val merged = LocalIndexedFile.Version(VersionVector(mapOf("a" to 1L)), HlcTimestamp.Zero, "a")
        val other = LocalIndexedFile.Version(VersionVector(mapOf("b" to 1L)), HlcTimestamp.Zero, "b")

        assertNull(local.copy(version = merged).evictRefusal(remote(version = merged)))
        assertEquals(EvictRefusal.Unmerged, local.copy(version = merged).evictRefusal(remote(version = other)))
    }

    @Test
    fun `whoever drives an active source evicts by hand, never the backup end`() {
        val offload = SyncMode.Offload(SyncMode.Offload.EvictPolicy.OlderThanDays(30))
        val mirror = SyncMode.Mirror(SyncMode.Mirror.ConflictResolution.Ask)
        val initiator = SourceEntry.Role.Initiator
        val follower = SourceEntry.Role.Follower

        for (mode in listOf(offload, SyncMode.Host, SyncMode.AutoUpload(null), mirror)) {
            assertTrue(sourceEntry(syncMode = mode, role = initiator).evictsByHand)
        }
        assertTrue(sourceEntry(syncMode = mirror, role = follower).evictsByHand)

        for (mode in listOf(offload, SyncMode.Host, SyncMode.AutoUpload(null))) {
            assertFalse(sourceEntry(syncMode = mode, role = follower).evictsByHand)
        }
        assertFalse(sourceEntry(syncMode = mirror, status = SourceEntry.Status.Pending).evictsByHand)
    }

    private fun remote(
        state: LocalIndexedFile.State = LocalIndexedFile.State.Present(),
        hash: ContentHash? = Hash,
        version: LocalIndexedFile.Version? = null,
    ) = RemoteIndexedFile(
        sourceId = local.sourceId,
        fileId = local.fileId,
        path = local.path,
        state = state,
        size = FileSize(0),
        modifiedAt = TestEpoch,
        hash = hash,
        version = version,
        seenAt = TestEpoch,
    )

    private companion object {
        const val Algorithm = "SHA-256"
        val Hash = ContentHash("hash", Algorithm)
    }
}
