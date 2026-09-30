package com.fserver.core.oneshot.impl

import com.fserver.core.oneshot.model.OneShotTransfer
import com.fserver.core.oneshot.model.OneShotTransferFile

/**
 * The status a transfer settles in once every file has: completed if anything arrived, failed if
 * nothing did. Null while a file is still pending.
 */
internal fun settledStatus(files: List<OneShotTransferFile>): OneShotTransfer.Status? {
    if (files.any { it.status == OneShotTransferFile.Status.Pending }) return null

    return if (files.any { it.status == OneShotTransferFile.Status.Completed }) {
        OneShotTransfer.Status.Completed
    } else {
        OneShotTransfer.Status.Failed(NothingTransferredReason)
    }
}

private const val NothingTransferredReason = "No file was transferred"
