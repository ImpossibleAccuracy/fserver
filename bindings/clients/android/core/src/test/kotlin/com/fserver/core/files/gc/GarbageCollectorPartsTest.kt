package com.fserver.core.files.gc

import android.content.ContextWrapper
import com.fserver.core.files.SourceLocation
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.LocalIndex
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.TestEpoch
import com.fserver.core.support.sourceEntry
import com.fserver.core.support.sourceFiles
import com.fserver.core.sync.fileops.FileEvictor
import com.fserver.files.FilesNode
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes

/** A part file a crashed placement left is dropped once it is old enough; a live one and real files stay. */
class GarbageCollectorPartsTest {

    @get:Rule
    val temp: TemporaryFolder = TemporaryFolder()

    private val clock = MutableTimeProvider(TestEpoch)
    private val storage = FakeStorage(clock = clock)

    private lateinit var gc: GarbageCollector
    private lateinit var root: File

    @Before
    fun setUp() {
        val node = FilesNode.create(ContextWrapper(null), stagingDir = temp.newFolder("staging"))
        gc = GarbageCollector(storage, node, clock, TestScope(), FileEvictor(storage, sourceFiles(storage, node), LocalIndex(storage, node, clock).writer))
        root = temp.newFolder("source-root")
    }

    @Test
    fun `a stale part goes, a fresh part and the files stay`() = runTest {
        storage.sources.upsert(sourceEntry(location = SourceLocation.Directory(root.absolutePath)))
        val stale = file("dir/a.jpg.x1.fserver-part", age = 2.hours)
        val fresh = file("dir/b.fserver-part.jpg", age = 5.minutes)
        val real = file("dir/c.jpg", age = 2.hours)

        gc.collectGarbage()

        assertFalse(stale.exists())
        assertTrue(fresh.exists())
        assertTrue(real.exists())
    }

    @Test
    fun `parts are swept once per interval, not every pass`() = runTest {
        storage.sources.upsert(sourceEntry(location = SourceLocation.Directory(root.absolutePath)))
        gc.collectGarbage()

        val stale = file("a.fserver-part", age = 2.hours)
        gc.collectGarbage()
        assertTrue(stale.exists())

        clock.advance(GarbageCollector.PartSweepInterval)
        file("a.fserver-part", age = 2.hours)
        gc.collectGarbage()
        assertFalse(stale.exists())
    }

    private fun file(path: String, age: Duration): File = File(root, path).apply {
        parentFile!!.mkdirs()
        writeText("bytes")
        setLastModified((clock.now() - age).toEpochMilliseconds())
    }
}
