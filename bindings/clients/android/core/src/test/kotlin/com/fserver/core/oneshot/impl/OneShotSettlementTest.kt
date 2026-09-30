package com.fserver.core.oneshot.impl

import com.fserver.core.oneshot.model.OneShotTransfer
import com.fserver.core.oneshot.model.OneShotTransferFile
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class OneShotSettlementTest {

    @Test
    fun `a transfer settles only once no file is pending`() {
        val done = file(0, OneShotTransferFile.Status.Completed)
        val failed = file(1, OneShotTransferFile.Status.Failed("gone"))
        val pending = file(2, OneShotTransferFile.Status.Pending)

        assertNull(settledStatus(listOf(done, pending)))
        assertEquals(OneShotTransfer.Status.Completed, settledStatus(listOf(done, failed)))
        assertTrue(settledStatus(listOf(failed)) is OneShotTransfer.Status.Failed)
    }

    private fun file(index: Int, status: OneShotTransferFile.Status) = OneShotTransferFile(
        index = index,
        name = "a",
        size = 1,
        locator = null,
        status = status,
    )
}
