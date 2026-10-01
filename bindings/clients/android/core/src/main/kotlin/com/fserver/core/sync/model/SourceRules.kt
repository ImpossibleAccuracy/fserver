package com.fserver.core.sync.model

import com.fserver.core.sync.index.LocalIndexedFile

// What each end of a source may do under its mode. Mirror is symmetric; every other mode is driven
// by the initiator alone, and the follower only answers it.

/** This device runs passes and file actions for the source. */
val SourceEntry.drivesSync: Boolean
    get() = syncMode is SyncMode.Mirror || role == SourceEntry.Role.Initiator

/** The peer runs passes for the source, so its requests to record versions are honoured. */
internal val SourceEntry.peerDrivesSync: Boolean
    get() = syncMode is SyncMode.Mirror || role == SourceEntry.Role.Follower

/** This device reports it's half to the peer before a pass: the side a pass runs against tells the one running it. */
internal val SourceEntry.sharesMetadata: Boolean
    get() = peerDrivesSync

/** The peer reports it's half to this device: the other end of [sharesMetadata]. */
internal val SourceEntry.receivesMetadata: Boolean
    get() = drivesSync

/** The peer may change the files this device holds for the source: push, overwrite, delete. */
internal val SourceEntry.acceptsPeerWrites: Boolean
    get() = peerDrivesSync

/** This device drops local bytes the peer holds: by the mode's criterion, and fetched copies by TTL. */
internal val SourceEntry.evictsLocally: Boolean
    get() = role == SourceEntry.Role.Initiator && (syncMode is SyncMode.Offload || syncMode is SyncMode.Host)

/**
 * The user may evict this device's bytes by hand, once the peer confirmably holds them. Only where
 * this device drives the source: never on the backup end of a one-way mode.
 */
val SourceEntry.evictsByHand: Boolean
    get() = drivesSync && status == SourceEntry.Status.Active

/** Files this device lacks may be fetched from the peer on demand. */
val SourceEntry.fetchesOnDemand: Boolean
    get() = when (syncMode) {
        is SyncMode.Mirror -> true
        is SyncMode.Offload, is SyncMode.Host -> role == SourceEntry.Role.Initiator
        is SyncMode.AutoUpload -> false
    }

/** A file this device lacks, in [localState] here (null: never held), may be fetched from the peer. */
fun SourceEntry.fetches(localState: LocalIndexedFile.State?): Boolean =
    fetchesOnDemand || (evictsByHand && localState is LocalIndexedFile.State.Evicted)
