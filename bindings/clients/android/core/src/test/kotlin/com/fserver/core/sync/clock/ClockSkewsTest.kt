package com.fserver.core.sync.clock

import com.fserver.core.support.sourceEntry
import com.fserver.core.sync.model.SyncMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** A peer whose clock is off, either way, must not get LWW: the winner would be picked by that clock. */
class ClockSkewsTest {

    private val skews = ClockSkews()
    private val lww = sourceEntry(deviceId = PeerId, syncMode = SyncMode.Mirror(SyncMode.Mirror.ConflictResolution.LastWriteWins))

    @Test
    fun `an unmeasured peer keeps the configured resolution`() {
        assertEquals(SyncMode.Mirror.ConflictResolution.LastWriteWins, skews.resolution(lww))
    }

    @Test
    fun `a peer off by more than the drift either way asks`() {
        skews.record(PeerId, offsetMs = 61_000)
        assertEquals(SyncMode.Mirror.ConflictResolution.Ask, skews.resolution(lww))

        skews.record(PeerId, offsetMs = -61_000)
        assertEquals(SyncMode.Mirror.ConflictResolution.Ask, skews.resolution(lww))
    }

    @Test
    fun `a clock set right brings last write wins back`() {
        skews.record(PeerId, offsetMs = 61_000)
        skews.record(PeerId, offsetMs = 500)

        assertEquals(SyncMode.Mirror.ConflictResolution.LastWriteWins, skews.resolution(lww))
    }

    @Test
    fun `a source with no conflicts to resolve has no resolution`() {
        skews.record(PeerId, offsetMs = 61_000)

        assertNull(skews.resolution(lww.copy(syncMode = SyncMode.Host)))
    }

    private companion object {
        const val PeerId = "peer"
    }
}
