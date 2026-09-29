package com.fserver.core.network.device

import com.fserver.core.network.device.model.ForeignDevice
import kotlinx.coroutines.flow.Flow

/**
 * Every device this process can see or remembers, split by *how* it is known.
 * The splits are disjoint - a device appears in exactly one of [connected], [handshaken],
 * [discovered] and [offline].
 */
interface OnlineDevices {
    /** Every device, visible or trusted - the union of the four splits. */
    val all: Flow<List<ForeignDevice>>

    /** Every device reachable right now - [connected], [handshaken] and [discovered] together. */
    val visible: Flow<List<ForeignDevice>>

    /** A session is up: requests can be sent without dialing anything. */
    val connected: Flow<List<ForeignDevice>>

    /**
     * Handshaken earlier in this process - probed or connected - but with no session up now.
     * Lives in memory only, so it is empty after a restart even for a device that is still trusted.
     */
    val handshaken: Flow<List<ForeignDevice>>

    /** Heard over discovery only. Nothing about it has been verified. */
    val discovered: Flow<List<ForeignDevice>>

    /** Trusted but not visible: built from the trust record, with no routes. */
    val offline: Flow<List<ForeignDevice>>

    /**
     * Trusted, visible or not - the devices a "reconnect" affordance is made of,
     * since these need no code comparison.
     */
    val known: Flow<List<ForeignDevice>>

    /** Visible and never trusted: reaching one of these costs the user a code comparison. */
    val unknown: Flow<List<ForeignDevice>>

    /** The device with [id], or null once it is neither visible nor trusted. */
    fun device(id: String): Flow<ForeignDevice?>
}
