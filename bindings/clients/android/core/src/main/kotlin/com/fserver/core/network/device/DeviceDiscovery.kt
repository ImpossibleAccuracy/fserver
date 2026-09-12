package com.fserver.core.network.device

import com.fserver.common.exception.DetectionFailedException
import com.fserver.core.network.TransportKind
import kotlinx.coroutines.flow.Flow

/**
 * Looking for other devices. What a scan turns up is read off [OnlineDevices.discovered], which is
 * where every other way of learning about a device lands too.
 *
 * Nothing starts by itself, and starting is per method rather than "scan everywhere": Android
 * gates several of the radios behind runtime permissions, so starting everything installed would
 * fail on whatever the user was never asked about.
 *
 * Being *found* is [DeviceAdvertising] - the two run over the same radios and under the same
 * permissions, but a device may do either without the other.
 */
interface DeviceDiscovery {
    /**
     * Methods scanning right now. Per-method rather than a single flag: several run at once, they
     * finish at wildly different times, and the UI has to say which one the user is still waiting on.
     */
    val runningMethods: Flow<Set<TransportKind>>

    /**
     * Runs [request] until it completes, publishing anything it finds through
     * [OnlineDevices.discovered]. May be long-running and/or never complete, depending on the
     * request.
     */
    @Throws(DetectionFailedException::class)
    suspend fun start(request: TransportKind): Result<Unit>

    /** Stops [request] if it is scanning, otherwise does nothing. */
    fun stop(request: TransportKind)
}
