package com.fserver.core.sync.limits

import com.fserver.common.model.FileSize
import com.fserver.core.sync.model.SourceEntry.Preferences.FileLimits
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UsedPercentTest {

    private val usage = SourceUsage(files = 25, bytes = 800)

    @Test
    fun `no caps report nothing`() {
        assertNull(FileLimits.None.usedPercent(usage))
    }

    @Test
    fun `the tightest cap wins`() {
        val limits = FileLimits(maxFiles = 100, maxTotalSize = FileSize(1_000))

        assertEquals(80f, limits.usedPercent(usage)!!, 0.001f)
    }

    @Test
    fun `a zero cap is ignored rather than dividing by it`() {
        val limits = FileLimits(maxFiles = 0, maxTotalSize = FileSize(1_600))

        assertEquals(50f, limits.usedPercent(usage)!!, 0.001f)
    }
}
