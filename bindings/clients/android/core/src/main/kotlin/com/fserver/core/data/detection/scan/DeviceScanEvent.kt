package com.fserver.core.data.detection.scan

import com.fserver.core.data.detection.connector.DeviceConnector

/**
 * What a running scan .
 *
 * Continuous methods (like mDNS) watch the network for as long as they run, so a peer can go away
 * again while the scan is still alive. One-shot methods only ever emit [Found].
 */
internal sealed interface DeviceScanEvent {
    /**
     * A peer showed up. It is not described yet - [connector] has to be asked who it is.
     */
    data class Found(val connector: DeviceConnector) : DeviceScanEvent

    /**
     * A peer went away and must be taken off the list.
     *
     * @property deviceId id the peer was announced under
     */
    data class Lost(val deviceId: String) : DeviceScanEvent
}
