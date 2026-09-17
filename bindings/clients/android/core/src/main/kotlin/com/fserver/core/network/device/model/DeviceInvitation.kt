package com.fserver.core.network.device.model

import com.fserver.common.model.Fingerprint

/** This device's own connection code, for another device to scan. */
data class DeviceInvitation(
    /** merged [routes] address */
    val payload: String,
    /** every address this device answers on */
    val routes: List<KnownRoute>,
    /** device own fingerprint */
    val fingerprint: Fingerprint,
)
