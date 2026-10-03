package com.fserver.core.storage.internal

import com.fserver.core.journal.JournalEvent
import com.fserver.core.journal.PassTally
import com.fserver.core.network.auth.AuthMethod
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import com.fserver.core.sync.progress.SyncFailure
import com.fserver.core.sync.progress.SyncFailureReason

/**
 * Turns a [JournalEvent] into its `kind` plus `attribute` rows and back - see [SourceRecords] for
 * why both directions live together. The source and device ids are columns, not attributes.
 * Enum fields are stored by name.
 */
internal object JournalRecords {
    const val Owner = "journal_entry"
    private const val Event = "event"

    // Kind discriminators.
    private const val PassCompleted = "PassCompleted"
    private const val PassFailed = "PassFailed"
    private const val PeerPassFailed = "PeerPassFailed"
    private const val SealedFilesUnreadable = "SealedFilesUnreadable"
    private const val ConflictHeld = "ConflictHeld"
    private const val ConflictResolved = "ConflictResolved"
    private const val ConflictDecisionDropped = "ConflictDecisionDropped"
    private const val ClockSkewed = "ClockSkewed"
    private const val IncompatibleDictionary = "IncompatibleDictionary"
    private const val ConnectionRefused = "ConnectionRefused"
    private const val DevicePaired = "DevicePaired"
    private const val DeviceForgotten = "DeviceForgotten"
    private const val SourceAdded = "SourceAdded"
    private const val SourceRemoved = "SourceRemoved"
    private const val SourceRequested = "SourceRequested"
    private const val SourceRequestRejected = "SourceRequestRejected"
    private const val SourceAcceptedByPeer = "SourceAcceptedByPeer"
    private const val SourceDisabledByPeer = "SourceDisabledByPeer"
    private const val OneShotFinished = "OneShotFinished"

    // Field names.
    private const val Sent = "sent"
    private const val Received = "received"
    private const val Deleted = "deleted"
    private const val Moved = "moved"
    private const val Evicted = "evicted"
    private const val Skipped = "skipped"
    private const val Reason = "reason"
    private const val Problem = "problem"
    private const val FileId = "fileId"
    private const val Path = "path"
    private const val Outcome = "outcome"
    private const val DecidedBy = "decidedBy"
    private const val OffsetMs = "offsetMs"
    private const val LocalVersion = "localVersion"
    private const val RemoteVersion = "remoteVersion"
    private const val Name = "name"
    private const val Method = "method"
    private const val Label = "label"
    private const val Mode = "mode"
    private const val Role = "role"
    private const val TransferId = "transferId"
    private const val PeerName = "peerName"
    private const val Direction = "direction"
    private const val Files = "files"

    fun kindOf(event: JournalEvent): String = when (event) {
        is JournalEvent.PassCompleted -> PassCompleted
        is JournalEvent.PassFailed -> PassFailed
        is JournalEvent.PeerPassFailed -> PeerPassFailed
        is JournalEvent.SealedFilesUnreadable -> SealedFilesUnreadable
        is JournalEvent.ConflictHeld -> ConflictHeld
        is JournalEvent.ConflictResolved -> ConflictResolved
        is JournalEvent.ConflictDecisionDropped -> ConflictDecisionDropped
        is JournalEvent.ClockSkewed -> ClockSkewed
        is JournalEvent.IncompatibleDictionary -> IncompatibleDictionary
        is JournalEvent.ConnectionRefused -> ConnectionRefused
        is JournalEvent.DevicePaired -> DevicePaired
        is JournalEvent.DeviceForgotten -> DeviceForgotten
        is JournalEvent.SourceAdded -> SourceAdded
        is JournalEvent.SourceRemoved -> SourceRemoved
        is JournalEvent.SourceRequested -> SourceRequested
        is JournalEvent.SourceRequestRejected -> SourceRequestRejected
        is JournalEvent.SourceAcceptedByPeer -> SourceAcceptedByPeer
        is JournalEvent.SourceDisabledByPeer -> SourceDisabledByPeer
        is JournalEvent.OneShotFinished -> OneShotFinished
    }

    // ---------------- writing ----------------

    fun attributesOf(event: JournalEvent): List<SourceRecords.Attribute> = buildList {
        fun put(field: String, value: Any) =
            add(SourceRecords.Attribute(Event, field, if (value is Enum<*>) value.name else value.toString()))

        when (event) {
            is JournalEvent.PassCompleted -> with(event.tally) {
                put(Sent, sent)
                put(Received, received)
                put(Deleted, deleted)
                put(Moved, moved)
                put(Evicted, evicted)
                put(Skipped, skipped)
            }

            is JournalEvent.PassFailed -> put(Reason, event.reason)
            is JournalEvent.PeerPassFailed -> put(Reason, event.reason)
            is JournalEvent.SealedFilesUnreadable -> put(Problem, event.problem)

            is JournalEvent.ConflictHeld -> {
                put(FileId, event.fileId)
                put(Path, event.path)
            }

            is JournalEvent.ConflictResolved -> {
                put(Path, event.path)
                put(Outcome, event.outcome)
                put(DecidedBy, event.decidedBy)
            }

            is JournalEvent.ConflictDecisionDropped -> {
                put(Path, event.path)
                put(Reason, event.reason)
            }

            is JournalEvent.ClockSkewed -> put(OffsetMs, event.offsetMs)

            is JournalEvent.IncompatibleDictionary -> {
                put(LocalVersion, event.localVersion)
                put(RemoteVersion, event.remoteVersion)
            }

            is JournalEvent.ConnectionRefused -> put(Reason, event.reason)

            is JournalEvent.DevicePaired -> {
                put(Name, event.name)
                put(Method, event.method)
            }

            is JournalEvent.DeviceForgotten -> put(Name, event.name)

            is JournalEvent.SourceAdded -> {
                put(Label, event.label)
                put(Mode, event.mode)
                put(Role, event.role)
            }

            is JournalEvent.SourceRemoved -> put(Label, event.label)

            is JournalEvent.SourceRequested -> {
                put(Label, event.label)
                put(Mode, event.mode)
            }

            is JournalEvent.SourceRequestRejected -> put(Label, event.label)
            is JournalEvent.SourceAcceptedByPeer -> put(Label, event.label)

            is JournalEvent.SourceDisabledByPeer -> {
                put(Label, event.label)
                put(Reason, event.reason)
            }

            is JournalEvent.OneShotFinished -> {
                put(TransferId, event.transferId)
                put(PeerName, event.peerName)
                put(Direction, event.direction)
                put(Outcome, event.outcome)
                put(Files, event.files)
            }
        }
    }

    // ---------------- reading ----------------

    /** Throws when the row does not rebuild an event; the caller skips it. */
    fun eventOf(
        kind: String,
        sourceId: String?,
        deviceId: String?,
        attributes: SourceRecords.Reader,
    ): JournalEvent {
        val fields = Fields(attributes)
        val source by lazy { requireNotNull(sourceId) { "$kind without a source" } }
        val device by lazy { requireNotNull(deviceId) { "$kind without a device" } }

        return with(fields) {
            when (kind) {
                PassCompleted -> JournalEvent.PassCompleted(
                    sourceId = source,
                    deviceId = device,
                    tally = PassTally(
                        sent = int(Sent),
                        received = int(Received),
                        deleted = int(Deleted),
                        moved = int(Moved),
                        evicted = int(Evicted),
                        skipped = int(Skipped),
                    ),
                )

                PassFailed -> JournalEvent.PassFailed(source, device, enum<SyncFailure.Reason>(Reason))
                PeerPassFailed -> JournalEvent.PeerPassFailed(source, device, enum<SyncFailureReason>(Reason))
                SealedFilesUnreadable -> JournalEvent.SealedFilesUnreadable(source, enum(Problem))
                ConflictHeld -> JournalEvent.ConflictHeld(source, device, string(FileId), string(Path))
                ConflictResolved -> JournalEvent.ConflictResolved(
                    source, device, string(Path), enum(Outcome), enum(DecidedBy),
                )

                ConflictDecisionDropped -> JournalEvent.ConflictDecisionDropped(source, string(Path), enum(Reason))
                ClockSkewed -> JournalEvent.ClockSkewed(device, long(OffsetMs))
                IncompatibleDictionary -> JournalEvent.IncompatibleDictionary(
                    device, int(LocalVersion), int(RemoteVersion),
                )

                ConnectionRefused -> JournalEvent.ConnectionRefused(device, enum(Reason))
                DevicePaired -> JournalEvent.DevicePaired(device, string(Name), enum<AuthMethod>(Method))
                DeviceForgotten -> JournalEvent.DeviceForgotten(device, string(Name))
                SourceAdded -> JournalEvent.SourceAdded(
                    source, device, string(Label), enum<SyncMode.Type>(Mode), enum<SourceEntry.Role>(Role),
                )

                SourceRemoved -> JournalEvent.SourceRemoved(source, device, string(Label))
                SourceRequested -> JournalEvent.SourceRequested(source, device, string(Label), enum(Mode))
                SourceRequestRejected -> JournalEvent.SourceRequestRejected(source, device, string(Label))
                SourceAcceptedByPeer -> JournalEvent.SourceAcceptedByPeer(source, device, string(Label))
                SourceDisabledByPeer -> JournalEvent.SourceDisabledByPeer(
                    source, device, string(Label), enum(Reason),
                )

                OneShotFinished -> JournalEvent.OneShotFinished(
                    transferId = string(TransferId),
                    deviceId = device,
                    peerName = string(PeerName),
                    direction = enum(Direction),
                    outcome = enum(Outcome),
                    files = int(Files),
                )

                else -> error("kind '$kind' is not one this build knows")
            }
        }
    }

    /** Strict reads of the `event` attributes: a missing or unreadable field throws. */
    private class Fields(private val attributes: SourceRecords.Reader) {
        fun string(field: String): String =
            attributes.string(Event, field) ?: error("no '$field'")

        fun int(field: String): Int =
            attributes.int(Event, field) ?: error("no readable '$field'")

        fun long(field: String): Long =
            attributes.long(Event, field) ?: error("no readable '$field'")

        inline fun <reified T : Enum<T>> enum(field: String): T = string(field).let { value ->
            enumValues<T>().firstOrNull { it.name == value } ?: error("'$field' '$value' is not one this build knows")
        }
    }
}
