package com.fserver.core.network.device

import com.fserver.core.network.device.model.ReachabilityFailure
import kotlinx.coroutines.flow.Flow

/**
 * Why devices this side tried to reach could not be reached.
 *
 * The other side of [OnlineDevices]: that one says who is visible,
 * this one says who was wanted and did not answer.
 */
interface DeviceReachability {
    /** Every device whose last attempt failed, most recent first. */
    val failures: Flow<List<ReachabilityFailure>>

    /** The standing failure for [deviceId], or null once it has been reached again. */
    fun device(deviceId: String): Flow<ReachabilityFailure?>
}
