package com.fserver.core.sync.conflict

import com.fserver.common.model.ContentHash
import com.fserver.common.model.FileSize
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.TestEpoch
import com.fserver.core.support.indexedFile
import com.fserver.core.support.sourceEntry
import com.fserver.core.sync.index.LocalIndexedFile
import com.fserver.core.sync.index.RemoteIndexedFile
import com.fserver.core.sync.model.SyncMode
import com.fserver.core.sync.runner.SyncRunner
import com.fserver.core.sync.runner.UploadStrategySelector
import com.fserver.core.sync.version.HlcTimestamp
import com.fserver.core.sync.version.VersionVector
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * A held conflict is never stored: both devices re-derive it from their own index and the peer's
 * last reported one. Getting that wrong either hides a conflict or shows one that is gone.
 */
class ConflictsControllerTest {

    private val clock = MutableTimeProvider()
    private val storage = FakeStorage(localDeviceId = LocalId, clock = clock)
    private val syncRunner = mockk<SyncRunner>(relaxed = true)

    private fun TestScope.controller() = ConflictsController(
        storage = storage,
        strategySelector = UploadStrategySelector(),
        syncRunner = syncRunner,
        timeProvider = clock,
        scope = backgroundScope,
    )

    @Test
    fun `only edits made on both sides of an asking source are held`() = runTest {
        storage.sources.upsert(sourceEntry(id = AskId, deviceId = PeerId, syncMode = Ask))
        storage.sources.upsert(sourceEntry(id = LwwId, deviceId = PeerId, syncMode = Lww))

        for (source in listOf(AskId, LwwId)) {
            // Edited on both sides.
            bothSides(source, "both", localHash = "a", remoteHash = "b", remoteVector = mapOf(PeerId to 1L))
            // Peer's edit already includes ours: a download, not a conflict.
            bothSides(source, "newer", localHash = "a", remoteHash = "b", remoteVector = mapOf(LocalId to 1L, PeerId to 1L))
        }

        val held = controller().pending.first { it.isNotEmpty() }

        assertEquals(listOf(AskId to "both"), held.map { it.sourceId to it.fileId })
        assertEquals(ConflictDecision.Choice.entries.toSet(), held.single().choices)
        assertEquals(PeerId, held.single().remote.deviceId)
    }

    @Test
    fun `a decision hides the conflict and starts a pass bound to the versions shown`() = runTest {
        storage.sources.upsert(sourceEntry(id = AskId, deviceId = PeerId, syncMode = Ask))
        bothSides(AskId, "both", localHash = "a", remoteHash = "b", remoteVector = mapOf(PeerId to 1L))
        val controller = controller()
        val conflict = controller.pending.first { it.isNotEmpty() }.single()

        controller.resolve(conflict, ConflictDecision.Choice.KeepRemote).getOrThrow()

        controller.pending.first { it.isEmpty() }
        val decision = storage.conflictDecisions.all.first().single()
        assertEquals(ConflictDecision.Choice.KeepRemote, decision.choice)
        assertEquals(conflict.local.version?.seen(), decision.local)
        assertEquals(conflict.remote.version?.seen(), decision.remote)
        verify { syncRunner.runSourceAsync(AskId) }
    }

    @Test
    fun `an edit past the decided versions brings the conflict back at once`() = runTest {
        storage.sources.upsert(sourceEntry(id = AskId, deviceId = PeerId, syncMode = Ask))
        bothSides(AskId, "both", localHash = "a", remoteHash = "b", remoteVector = mapOf(PeerId to 1L))
        val controller = controller()
        val conflict = controller.pending.first { it.isNotEmpty() }.single()
        controller.resolve(conflict, ConflictDecision.Choice.KeepLocal).getOrThrow()
        controller.pending.first { it.isEmpty() }

        bothSides(AskId, "both", localHash = "a", remoteHash = "c", remoteVector = mapOf(PeerId to 2L), remoteHlc = 30)

        assertEquals(listOf("both"), controller.pending.first { it.isNotEmpty() }.map { it.fileId })
    }

    @Test
    fun `an evicted peer copy cannot be kept`() = runTest {
        storage.sources.upsert(sourceEntry(id = AskId, deviceId = PeerId, syncMode = Ask))
        bothSides(
            AskId, "both", localHash = "a", remoteHash = "b", remoteVector = mapOf(PeerId to 1L),
            remoteState = LocalIndexedFile.State.Evicted(TestEpoch),
        )

        val conflict = controller().pending.first { it.isNotEmpty() }.single()

        assertEquals(setOf(ConflictDecision.Choice.KeepLocal), conflict.choices)
    }

    private suspend fun bothSides(
        sourceId: String,
        fileId: String,
        localHash: String,
        remoteHash: String,
        remoteVector: Map<String, Long>,
        remoteState: LocalIndexedFile.State = LocalIndexedFile.State.Present(),
        remoteHlc: Long = 20,
    ) {
        storage.index.markProcessed(
            listOf(
                indexedFile(id = "$sourceId-$fileId", sourceId = sourceId, fileId = fileId, path = "$fileId.txt")
                    .copy(
                        hash = ContentHash(localHash, "SHA-256"),
                        version = LocalIndexedFile.Version(
                            vector = VersionVector(mapOf(LocalId to 1L)),
                            hlc = HlcTimestamp(10),
                            originDevice = LocalId,
                        ),
                    )
            )
        )

        storage.remoteIndex.upsert(
            deviceId = PeerId,
            file = RemoteIndexedFile(
                sourceId = sourceId,
                fileId = fileId,
                path = "$fileId.txt",
                state = remoteState,
                size = FileSize(0),
                modifiedAt = TestEpoch,
                hash = ContentHash(remoteHash, "SHA-256"),
                version = LocalIndexedFile.Version(
                    vector = VersionVector(remoteVector),
                    hlc = HlcTimestamp(remoteHlc),
                    originDevice = PeerId,
                ),
                seenAt = TestEpoch,
            ),
        )
    }

    private companion object {
        const val LocalId = "device-local"
        const val PeerId = "device-peer"
        const val AskId = "source-ask"
        const val LwwId = "source-lww"

        val Ask = SyncMode.Mirror(SyncMode.Mirror.ConflictResolution.Ask)
        val Lww = SyncMode.Mirror(SyncMode.Mirror.ConflictResolution.LastWriteWins)
    }
}
