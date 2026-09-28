package com.fserver.core.disk

import com.fserver.core.files.preview.EvictingFile
import com.fserver.core.files.preview.EvictionPreviewer
import com.fserver.files.FilesNode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

/** Eviction previews get their own category and are taken out of the one they sit in. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class DiskUsagePreviewsTest {

    private val context = RuntimeEnvironment.getApplication()
    private val node = FilesNode.create(context)

    @Test
    fun `previews reported in app data move out of service bytes`() = runBlocking {
        File(context.noBackupFilesDir, "previews").apply { writeBytes(ByteArray(100)) }
        val baseline = footprint(previewer = null)

        val footprint = footprint { mapOf(StoreType.AppData to 100L) }

        assertEquals(100, footprint.evictionPreviewBytes)
        assertEquals(baseline.serviceBytes - 100, footprint.serviceBytes)
        assertEquals(baseline.totalBytes, footprint.totalBytes)
    }

    @Test
    fun `a report larger than the walk never drives a category below zero`() = runBlocking {
        val footprint = footprint { mapOf(StoreType.Cache to Long.MAX_VALUE / 2) }

        assertEquals(0, footprint.cacheBytes)
    }

    @Test
    fun `a failing previewer leaves every category as it was`() = runBlocking {
        val baseline = footprint(previewer = null)

        val footprint = footprint { error("unreadable") }

        assertEquals(0, footprint.evictionPreviewBytes)
        assertEquals(baseline, footprint)
    }

    private fun footprint(usage: suspend () -> Map<StoreType, Long>) = footprint(
        object : EvictionPreviewer {
            override suspend fun capture(file: EvictingFile) = Unit
            override suspend fun usage() = usage()
        },
    )

    private fun footprint(previewer: EvictionPreviewer?): AppFootprint = runBlocking {
        DiskUsageRepository(context, node, previewer).usage.first().footprint
    }
}
