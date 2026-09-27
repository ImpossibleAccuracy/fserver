package com.fserver.core.sync.model

// What each end of a source may do under its mode. Mirror is symmetric; every other mode is driven
// by the initiator alone, and the follower only answers it.

/** This device runs passes and file actions for the source. */
val SourceEntry.drivesSync: Boolean
    get() = syncMode is SyncMode.Mirror || role == SourceEntry.Role.Initiator

/** The peer runs passes for the source, so its requests to record versions are honoured. */
internal val SourceEntry.peerDrivesSync: Boolean
    get() = syncMode is SyncMode.Mirror || role == SourceEntry.Role.Follower

/**
 * The peer may change the files this device holds for the source: push, overwrite, delete.
 * TODO: a read-only Host follower refuses these, once Host has access rights.
 */
internal val SourceEntry.acceptsPeerWrites: Boolean
    get() = peerDrivesSync

/** This device drops local bytes the peer holds: by the mode's criterion, and fetched copies by TTL. */
internal val SourceEntry.evictsLocally: Boolean
    get() = role == SourceEntry.Role.Initiator && (syncMode is SyncMode.Offload || syncMode is SyncMode.Host)

/** Files this device lacks may be fetched from the peer on demand. */
val SourceEntry.fetchesOnDemand: Boolean
    get() = when (syncMode) {
        is SyncMode.Mirror -> true
        is SyncMode.Offload, is SyncMode.Host -> role == SourceEntry.Role.Initiator
        is SyncMode.AutoUpload -> false
    }
