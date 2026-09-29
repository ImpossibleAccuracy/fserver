package com.fserver.core.sync.lease

import com.fserver.core.sync.model.SourceEntry

/** A lease this device holds, as the pass running under it sees it. */
internal interface HeldLease {
    /** The source as agreed with the peer: ours, with the initiator's mode adopted if ours was stale. */
    val source: SourceEntry

    /**
     * Takes the lease again over a fresh session, after the one it was granted on dropped: the peer
     * frees what a dead session held. Throws when the peer cannot be reached or will not grant it.
     */
    suspend fun renew()
}