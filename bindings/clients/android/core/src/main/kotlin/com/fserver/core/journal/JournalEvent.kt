package com.fserver.core.journal

import com.fserver.core.network.auth.AuthMethod
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import com.fserver.core.sync.progress.SyncFailure
import com.fserver.core.sync.progress.SyncFailureReason

/**
 * One thing the engine did or ran into, as the activity journal keeps it. One subclass per kind of
 * record; everything a screen picks its wording from is typed, never a sentence.
 */
sealed interface JournalEvent {
    /** The source this is about, if any. Issues about a removed source are solved by it. */
    val sourceId: String? get() = null

    /** The peer this is about, if any. Issues about a forgotten device are solved by it. */
    val deviceId: String? get() = null

    /**
     * A problem that stays open until the engine sees it gone or the user dismisses it. Repeats
     * fold into the open entry with the same [key]; one after it was solved opens a new entry.
     */
    sealed interface Issue : JournalEvent {
        val key: String
    }

    // ---------------- sync ----------------

    /**
     * A pass this device drove got through and moved something. Passes with nothing to do are not
     * kept. [label] and [mode] are a snapshot, so the entry still reads once the source is gone.
     */
    data class PassCompleted(
        override val sourceId: String,
        override val deviceId: String,
        val label: String,
        val mode: SyncMode.Type,
        val tally: PassTally,
    ) : JournalEvent

    /** A file this device did not hold was fetched from the peer because the user opened it. */
    data class FileFetched(
        override val sourceId: String,
        override val deviceId: String,
        val path: String,
        val bytes: Long,
    ) : JournalEvent

    /** A pass this device drove gave up. Solved by the next pass over the source that gets through. */
    data class PassFailed(
        override val sourceId: String,
        override val deviceId: String,
        val reason: SyncFailure.Reason,
    ) : Issue {
        override val key: String get() = keyOf(sourceId)

        companion object {
            internal fun keyOf(sourceId: String) = "pass:$sourceId"
        }
    }

    /** The peer drove a pass against this device and said it did not get through. */
    data class PeerPassFailed(
        override val sourceId: String,
        override val deviceId: String,
        val reason: SyncFailureReason,
    ) : JournalEvent

    /** Files sealed at rest that this device cannot open. Solved by the next pass that gets through. */
    data class SealedFilesUnreadable(
        override val sourceId: String,
        val problem: Problem,
    ) : Issue {
        override val key: String get() = keyOf(sourceId)

        enum class Problem {
            /** Sealed under a key this device does not hold - reinstalled, or moved from another device. */
            MissingKey,

            /** Sealed by a cipher this build does not know. */
            UnknownCipher,

            /** Damaged, or tampered with. */
            Corrupted,
        }

        companion object {
            internal fun keyOf(sourceId: String) = "sealed:$sourceId"
        }
    }

    // ---------------- conflicts ----------------

    /** A conflict waits for the user. Solved once carried out, or once it stops being one. */
    data class ConflictHeld(
        override val sourceId: String,
        override val deviceId: String,
        val fileId: String,
        val path: String,
    ) : Issue {
        override val key: String get() = keyOf(sourceId, fileId)

        companion object {
            internal fun keyOf(sourceId: String, fileId: String) = "conflict:$sourceId:$fileId"
        }
    }

    data class ConflictResolved(
        override val sourceId: String,
        override val deviceId: String,
        val path: String,
        val outcome: Outcome,
        val decidedBy: DecidedBy,
    ) : JournalEvent {
        enum class Outcome { KeptLocal, KeptRemote, KeptBoth }

        enum class DecidedBy { LastWriteWins, User }
    }

    /** The user's choice on a conflict was not carried out. */
    data class ConflictDecisionDropped(
        override val sourceId: String,
        val path: String,
        val reason: Reason,
    ) : JournalEvent {
        enum class Reason {
            /** A side changed since the user decided: they never saw what it would overwrite. */
            SideChanged,

            /** The chosen side was evicted since. */
            ChoiceUnavailable,

            /** Settled meanwhile - resolved on the peer, or edited past the conflict. */
            NoLongerConflicts,
        }
    }

    // ---------------- peers ----------------

    /** The peer's clock is off by more than conflict resolution tolerates. Solved once it is back in range. */
    data class ClockSkewed(
        override val deviceId: String,
        /** The peer's clock minus ours. */
        val offsetMs: Long,
    ) : Issue {
        override val key: String get() = keyOf(deviceId)

        companion object {
            internal fun keyOf(deviceId: String) = "clock:$deviceId"
        }
    }

    /** The peer speaks a message dictionary this build declined. Solved by the next connection that works. */
    data class IncompatibleDictionary(
        override val deviceId: String,
        val localVersion: Int,
        val remoteVersion: Int,
    ) : Issue {
        override val key: String get() = keyOf(deviceId)

        companion object {
            internal fun keyOf(deviceId: String) = "dictionary:$deviceId"
        }
    }

    /** A dial reached the peer and the handshake did not go through. Solved by the next connection that works. */
    data class ConnectionRefused(
        override val deviceId: String,
        val reason: Reason,
    ) : Issue {
        override val key: String get() = keyOf(deviceId)

        enum class Reason {
            /** The peer did not accept our credentials, or we did not accept its. */
            AuthenticationRejected,

            /** Another device answered under the address of the one dialled. */
            IdentityMismatch,

            /** The handshake broke for any other reason. */
            HandshakeFailed,
        }

        companion object {
            internal fun keyOf(deviceId: String) = "connect:$deviceId"
        }
    }

    data class DevicePaired(
        override val deviceId: String,
        val name: String,
        val method: AuthMethod,
    ) : JournalEvent

    data class DeviceForgotten(
        override val deviceId: String,
        val name: String,
    ) : JournalEvent

    // ---------------- sources ----------------

    /** Registered here: by this device's user, or by accepting the peer's ask. */
    data class SourceAdded(
        override val sourceId: String,
        override val deviceId: String,
        val label: String,
        val mode: SyncMode.Type,
        val role: SourceEntry.Role,
    ) : JournalEvent

    data class SourceRemoved(
        override val sourceId: String,
        override val deviceId: String,
        val label: String,
    ) : JournalEvent

    /** The peer asked this device to host a source. */
    data class SourceRequested(
        override val sourceId: String,
        override val deviceId: String,
        val label: String,
        val mode: SyncMode.Type,
    ) : JournalEvent

    /** This device's user turned the peer's ask down. */
    data class SourceRequestRejected(
        override val sourceId: String,
        override val deviceId: String,
        val label: String,
    ) : JournalEvent

    /** The peer agreed to host a source this device registered. */
    data class SourceAcceptedByPeer(
        override val sourceId: String,
        override val deviceId: String,
        val label: String,
    ) : JournalEvent

    /** The peer will not sync a source any more, so it was disabled here. */
    data class SourceDisabledByPeer(
        override val sourceId: String,
        override val deviceId: String,
        val label: String,
        val reason: Reason,
    ) : JournalEvent {
        enum class Reason {
            /** Turned the ask down. */
            Refused,

            /** Dropped its half after syncing. */
            Removed,
        }
    }

    /** Settings of a source changed: by this device's user, or taken over from the peer. */
    data class SourceChanged(
        override val sourceId: String,
        override val deviceId: String,
        val label: String,
        val changes: Set<Change>,
        val by: ChangedBy,
    ) : JournalEvent {
        enum class Change { ModeSettings, DeviceConstraints, FileLimits, Encryption }

        enum class ChangedBy { User, Peer }
    }

    // ---------------- encryption ----------------

    /** Files of a source were brought in line with its encryption policy. */
    data class EncryptionMigrated(
        override val sourceId: String,
        val target: EncryptionTarget,
        val files: Int,
    ) : JournalEvent

    /** Some files could not be brought in line. Solved by a later migration that leaves none behind. */
    data class EncryptionIncomplete(
        override val sourceId: String,
        val target: EncryptionTarget,
        val failed: Int,
    ) : Issue {
        override val key: String get() = keyOf(sourceId)

        companion object {
            internal fun keyOf(sourceId: String) = "encryption:$sourceId"
        }
    }

    enum class EncryptionTarget { Encrypted, Decrypted }

    // ---------------- one-shot ----------------

    data class OneShotFinished(
        val transferId: String,
        override val deviceId: String,
        val peerName: String,
        val direction: Direction,
        val outcome: Outcome,
        val files: Int,
    ) : JournalEvent {
        enum class Direction { Outgoing, Incoming }

        enum class Outcome { Completed, Declined, Cancelled, Failed }
    }
}

/** What a pass moved, by kind of action. Failed actions are not counted. */
data class PassTally(
    val sent: Int = 0,
    val received: Int = 0,
    /** Deleted on this device, following the peer. */
    val deletedHere: Int = 0,
    /** Deleted on the peer, following this device. */
    val deletedOnPeer: Int = 0,
    val moved: Int = 0,
    val evicted: Int = 0,
    /** New files left out for a file limit. */
    val skipped: Int = 0,
) {
    val isEmpty: Boolean get() = this == PassTally()
}
