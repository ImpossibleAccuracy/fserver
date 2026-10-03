package com.fserver.core.sync.conflict

import com.fserver.core.support.TestEpoch
import com.fserver.core.support.sourceEntry
import com.fserver.core.sync.model.SyncMode
import com.fserver.files.upload.FileAction
import com.fserver.files.upload.FileId
import com.fserver.files.upload.FileRecord
import com.fserver.files.upload.UploadDecisions
import org.junit.Assert.assertEquals
import org.junit.Test

/** A decision no pass will ever carry out must not outlive the conflict it was made for. */
class SettledDecisionsTest {

    private val asking = sourceEntry(syncMode = SyncMode.Mirror(SyncMode.Mirror.ConflictResolution.Ask))

    @Test
    fun `a file resolved on the peer first leaves its decision settled`() {
        val plan = UploadDecisions(
            listOf(
                FileAction.Conflict(record("held"), record("held"), "edited on both sides"),
                FileAction.ComputeHash(FileId("hashing"), record("hashing"), record("hashing"), "not hashed yet"),
                FileAction.Download(record("moved"), reason = "newer remotely"),
            )
        )

        val settled = settledDecisions(SyncMode.Mirror.ConflictResolution.Ask, plan, listOf(decision("held"), decision("hashing"), decision("moved"), decision("gone")))

        assertEquals(listOf("moved", "gone"), settled.map { it.fileId })
    }

    @Test
    fun `a source that stopped asking settles every decision it had`() {
        val plan = UploadDecisions(listOf(FileAction.Conflict(record("held"), record("held"), "edited on both sides")))

        assertEquals(listOf("held"), settledDecisions(SyncMode.Mirror.ConflictResolution.LastWriteWins, plan, listOf(decision("held"))).map { it.fileId })
    }

    private fun record(id: String) = FileRecord(
        id = FileId(id),
        path = "$id.txt",
        locator = null,
        state = FileRecord.State.Present(),
        content = null,
        metadata = FileRecord.Metadata(size = 1, lastModified = TestEpoch, version = null),
    )

    private fun decision(fileId: String) = ConflictDecision(
        sourceId = asking.id,
        fileId = fileId,
        choice = ConflictDecision.Choice.KeepLocal,
        local = null,
        remote = null,
        decidedAt = TestEpoch,
    )
}
