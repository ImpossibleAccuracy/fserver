package com.fserver.core.sync.model

import com.fserver.core.files.SourceLocation
import kotlin.time.Instant

/**
 * One registered directory plus the [SyncMode] it runs under - the pair the engine works from.
 *
 * Constructor is public because a storage backend has to rebuild one from its own columns. Hosts
 * still register through [com.fserver.core.sync.SourcesController.addSource], which is what assigns [id].
 */
data class SourceEntry(
    val id: String,
    /**
     * The peer this source syncs with. Load-bearing beyond routing: it is what the serving side
     * checks a request against, so a source only ever answers the one device it is paired with.
     */
    val deviceId: String,
    /** What to walk. */
    val location: SourceLocation.Persistable,
    /** The initiator's directory as a person reads it. */
    val originPath: String,
    /** What to do with the files found there. */
    val syncMode: SyncMode,
    val role: Role,
    val status: Status,
    /** Display name, supplied by whoever registered the source. */
    val label: String,
    val createdAt: Instant,
    /** null until a sync pass has processed this source once. */
    val lastSyncedAt: Instant? = null,
) {
    /** Which end of the source this device is. */
    enum class Role {
        /** Registered the source and asked the peer to host it. Files originate on this side. */
        Initiator,

        /** Accepted the peer's ask, and holds what arrives in the location it picked. */
        Follower,
    }

    /**
     * Whether a source may sync at all.
     *
     * A source works only while both devices hold a record under its id, and that is not something
     * either side decides alone: the peer's user accepts it, and can drop it again later. Passes
     * run under [Active] and no other status.
     */
    sealed interface Status {
        /**
         * Registered here, waiting on the peer's user.
         * This device runs no pass yet - but it still answers the peer, whose acceptance may be on its way.
         */
        data object Pending : Status

        /** Both devices hold it. The only status a pass runs under. */
        data object Active : Status

        /**
         * The peer refused the source, or dropped its half later.
         *
         * Kept rather than removed: the record is what stops the next pass from asking again, and
         * what the user reads to find out why a source went quiet.
         */
        data class Disabled(val reason: String) : Status
    }
}
