package com.fserver.core.oneshot

import android.net.Uri
import com.fserver.common.utils.runBackgroundJob
import com.fserver.core.files.SourceLocation
import com.fserver.core.files.ensureSourceReachable
import com.fserver.core.files.scan.ScannedContent
import com.fserver.core.oneshot.impl.OneShotExchange
import com.fserver.core.oneshot.model.OneShotTransfer
import com.fserver.core.requirement.RequirementsChecker

/**
 * One-shot transfers: send files to a device, answer what a device sends. Engine actions only -
 * the history lives on `OneShotTransfersRepository` in `:core:storage`, and bytes on the move in
 * `SyncProgressRepository.oneShotTransfers`.
 *
 * Auto-accepting is the host's call: it watches for pending incoming transfers and [accept]s them.
 */
class OneShotTransfersController internal constructor(
    private val exchange: OneShotExchange,
    private val requirementsChecker: RequirementsChecker,
) {
    /**
     * Offers [files] - what a scan of [origin] turned up - to [deviceId]. Returns once recorded;
     * the offer goes out in the background, and again when the peer is back.
     */
    suspend fun send(
        deviceId: String,
        origin: SourceLocation.Persistable,
        files: List<ScannedContent.File>,
    ): Result<OneShotTransfer> =
        runBackgroundJob { exchange.create(deviceId, origin, files) }

    /**
     * [send] for `content://` [uris] another app shared. Copied into app storage first: the grant
     * ends with the sharing task, the copy lasts until the transfer does.
     */
    suspend fun sendShared(deviceId: String, uris: List<Uri>): Result<OneShotTransfer> =
        runBackgroundJob { exchange.createShared(deviceId, uris.map { it.toString() }) }

    /** Accepts a pending incoming transfer, writing what arrives into [destination]. */
    suspend fun accept(transferId: String, destination: SourceLocation.Hostable): Result<Unit> =
        runBackgroundJob {
            requirementsChecker.ensureSourceReachable(destination)
            exchange.accept(transferId, destination)
        }

    suspend fun decline(transferId: String): Result<Unit> =
        runBackgroundJob { exchange.decline(transferId) }

    /** Stops a transfer in either direction. What was already received stays. */
    suspend fun cancel(transferId: String): Result<Unit> =
        runBackgroundJob { exchange.cancel(transferId) }

    /** Tries an outgoing transfer again now, rather than when the peer next connects. */
    suspend fun retry(transferId: String): Result<Unit> =
        runBackgroundJob { exchange.retry(transferId) }
}
