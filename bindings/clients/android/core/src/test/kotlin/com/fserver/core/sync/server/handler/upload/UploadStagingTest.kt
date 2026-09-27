package com.fserver.core.sync.server.handler.upload

import android.content.ContextWrapper
import com.fserver.core.sync.runner.FileEvictor
import com.fserver.core.files.gc.GarbageCollector
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.TestEpoch
import com.fserver.core.support.sourceEntry
import com.fserver.core.sync.index.IndexedFileKey
import com.fserver.files.FilesNode
import com.fserver.files.upload.FileId
import com.fserver.files.upload.FileRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours

/** What garbage collection takes from staging, and what it must leave for a resume. */
class UploadStagingTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clock = MutableTimeProvider()
    private val storage = FakeStorage(clock = clock)

    private lateinit var root: File
    private lateinit var staging: UploadStaging
    private lateinit var garbageCollector: GarbageCollector

    private val key = IndexedFileKey(fileId = "file-1", sourceId = "source-1")

    @Before
    fun setUp() = runBlocking {
        root = temp.newFolder("staging")
        val node = FilesNode.create(ContextWrapper(null), root)
        staging = UploadStaging(storage, node, clock)
        garbageCollector = GarbageCollector(storage, node, clock, scope, FileEvictor(storage, node, clock))
        storage.sources.upsert(sourceEntry(id = key.sourceId))
    }

    @After
    fun tearDown() {
        scope.cancel()
    }

    @Test
    fun `an upload touched recently survives collection`() = runTest {
        staging.open(key, DeviceId, record())
        clock.advance(1.hours)

        garbageCollector.collectGarbage()

        assertNotNull(storage.uploads.find(key))
        assertTrue(stagedFile().exists())
    }

    @Test
    fun `an upload nobody came back for is dropped with its bytes`() = runTest {
        staging.open(key, DeviceId, record())
        clock.advance(2.days)

        garbageCollector.collectGarbage()

        assertNull(storage.uploads.find(key))
        assertFalse(stagedFile().exists())
    }

    @Test
    fun `a row whose bytes the system cleared is dropped`() = runTest {
        staging.open(key, DeviceId, record())
        stagedFile().delete()

        garbageCollector.collectGarbage()

        assertNull(storage.uploads.find(key))
    }

    @Test
    fun `staged bytes with no row are dropped once past the grace period`() = runTest {
        val orphan = File(root, "source-1/file-9/data").apply {
            parentFile!!.mkdirs()
            writeText("orphan")
            setLastModified(TestEpoch.toEpochMilliseconds())
        }
        clock.advance(2.hours)

        garbageCollector.collectGarbage()

        assertFalse(orphan.exists())
    }

    @Test
    fun `another peer pushing the same file does not resume the first one's bytes`() = runTest {
        staging.open(key, DeviceId, record())
        storage.uploads.checkpoint(key, offset = 3, at = clock.now())

        val opened = staging.open(key, "device-other", record())

        assertTrue(opened.committed == 0L)
    }

    private fun stagedFile() = File(root, "${key.sourceId}/${key.fileId}/data")

    private fun record() = FileRecord(
        id = FileId(key.fileId),
        path = "a.bin",
        locator = null,
        state = FileRecord.State.Present(),
        content = null,
        metadata = FileRecord.Metadata(size = 10, lastModified = TestEpoch, version = null),
    )

    private companion object {
        const val DeviceId = "device-peer"
    }
}
