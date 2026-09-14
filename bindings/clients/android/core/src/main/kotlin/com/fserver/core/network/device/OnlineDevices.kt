package com.fserver.core.network.device

import com.fserver.core.network.device.model.ForeignDevice
import kotlinx.coroutines.flow.Flow

/**
 * Every device this process can currently see, split by *how* it is seen.
 * The splits are disjoint - a device appears in exactly one of [connected], [handshaken] and [discovered].
 */
interface OnlineDevices {
    /** Every visible device, whatever the claim behind it - the union of the three splits. */
    val all: Flow<List<ForeignDevice>>

    /** A session is up: requests can be sent without dialing anything. */
    val connected: Flow<List<ForeignDevice>>

    /**
     * Handshaken earlier in this process - probed or connected - but with no session up now.
     * Lives in memory only, so it is empty after a restart even for a device that is still trusted.
     */
    val handshaken: Flow<List<ForeignDevice>>

    /** Heard over discovery only. Nothing about it has been verified. */
    val discovered: Flow<List<ForeignDevice>>

    /**
     * Visible *and* trusted - the intersection a "reconnect" affordance is made of,
     * since these are the devices that need no code comparison.
     */
    val known: Flow<List<ForeignDevice>>

    /** Visible and never trusted: reaching one of these costs the user a code comparison. */
    val unknown: Flow<List<ForeignDevice>>

    /** The device with [id], or null once it is no longer visible. */
    fun device(id: String): Flow<ForeignDevice?>
}
