package com.fserver.core.network.device.model

/** This device, as peers see it. Counterpart to [ForeignDevice]. */
data class LocalDevice(
    /** Stable across restarts - peers follow this device by it. */
    val deviceId: String,
    val displayName: String,
    val kind: DeviceKind?,
)
