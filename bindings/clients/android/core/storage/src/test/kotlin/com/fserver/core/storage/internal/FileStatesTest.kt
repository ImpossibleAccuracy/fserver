package com.fserver.core.storage.internal

import com.fserver.core.sync.index.LocalIndexedFile
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Instant

class FileStatesTest {

    @Test
    fun `every state reads back as written`() {
        val at = Instant.fromEpochMilliseconds(1_700_000_000_000)
        val states = listOf(
            LocalIndexedFile.State.Present(),
            LocalIndexedFile.State.Present(pinned = true),
            LocalIndexedFile.State.Present(fetchedAt = at),
            LocalIndexedFile.State.Evicted(evictedAt = at),
            LocalIndexedFile.State.Deleted(deletedAt = at),
        )

        for (state in states) {
            val read = FileStates.read(
                state = FileStates.nameOf(state),
                pinned = FileStates.pinnedOf(state),
                changedAtEpochMs = FileStates.changedAtOf(state),
            )

            assertEquals(state, read)
        }
    }
}
