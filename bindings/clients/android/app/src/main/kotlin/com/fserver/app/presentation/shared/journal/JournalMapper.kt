package com.fserver.app.presentation.shared.journal

import android.text.format.DateUtils
import com.fserver.app.R
import com.fserver.app.presentation.composable.model.PeerUi
import com.fserver.app.presentation.composable.model.fileName
import com.fserver.app.presentation.composable.model.labelRes
import com.fserver.app.presentation.composable.model.peerOf
import com.fserver.app.presentation.composable.model.peers
import com.fserver.app.presentation.model.UiText
import com.fserver.app.presentation.screens.source.shared.model.titleRes
import com.fserver.app.presentation.screens.source.shared.model.toUi
import com.fserver.app.presentation.shared.error.messageRes
import com.fserver.app.presentation.shared.error.peerDetailRes
import com.fserver.app.presentation.shared.journal.model.JournalEntryUi
import com.fserver.app.presentation.shared.journal.model.JournalGroupUi
import com.fserver.app.presentation.shared.journal.model.JournalKindUi
import com.fserver.core.journal.ActivityJournal
import com.fserver.core.journal.JournalEntry
import com.fserver.core.journal.JournalEvent
import com.fserver.core.journal.PassTally
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.storage.RegisteredSourcesRepository
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlin.math.abs

fun ActivityJournal.journalFeed(
    devices: DevicesRepository,
    sources: RegisteredSourcesRepository,
): Flow<List<JournalEntryUi>> = combine(
    entries,
    devices.peers(),
    sources.sources,
) { entries, peers, registered ->
    val labels = registered.associate { it.id to it.label }
    entries.map { it.toUi(peers, labels) }
}

fun JournalEntry.toUi(peers: Map<String, PeerUi>, labels: Map<String, String>): JournalEntryUi {
    val names = Names(peers, labels)
    val base = JournalEntryUi(
        id = id,
        group = event.group,
        kind = JournalKindUi.Failed,
        title = UiText.Text(""),
        at = at,
        issue = issue?.let { JournalEntryUi.IssueUi(open = !it.solved, occurrences = it.occurrences) },
        sourceId = event.sourceId,
        deviceId = event.deviceId,
    )

    return when (val event = event) {
        is JournalEvent.PassCompleted -> base.copy(
            kind = event.kind,
            title = UiText.of(
                if (event.mode.offloads) R.string.journal_pass_offloaded else R.string.journal_pass_synced,
                event.label,
            ),
            details = event.tally.details(names.peer(event.deviceId)),
        )

        is JournalEvent.FileFetched -> base.copy(
            kind = JournalKindUi.Fetched,
            title = UiText.of(R.string.journal_file_fetched, event.path.fileName()),
            details = listOf(UiText.Size(R.string.journal_size, event.bytes)),
        )

        is JournalEvent.PassFailed -> base.copy(
            title = UiText.of(R.string.journal_pass_failed, names.source(event.sourceId)),
            details = listOf(UiText.of(event.reason.messageRes)),
        )

        is JournalEvent.PeerPassFailed -> base.copy(
            title = UiText.of(
                R.string.journal_peer_pass_failed,
                names.peer(event.deviceId),
                names.source(event.sourceId),
            ),
            details = listOf(UiText.of(event.reason.peerDetailRes)),
        )

        is JournalEvent.SealedFilesUnreadable -> base.copy(
            title = UiText.of(R.string.journal_sealed_unreadable, names.source(event.sourceId)),
            details = listOf(
                UiText.of(
                    when (event.problem) {
                        JournalEvent.SealedFilesUnreadable.Problem.MissingKey -> R.string.journal_sealed_missing_key
                        JournalEvent.SealedFilesUnreadable.Problem.UnknownCipher -> R.string.journal_sealed_unknown_cipher
                        JournalEvent.SealedFilesUnreadable.Problem.Corrupted -> R.string.journal_sealed_corrupted
                    }
                )
            ),
        )

        is JournalEvent.ConflictHeld -> base.copy(
            kind = JournalKindUi.Conflict,
            title = UiText.of(R.string.activity_conflict_title, event.path.fileName()),
            details = listOf(UiText.of(R.string.journal_conflict_held, names.peer(event.deviceId))),
        )

        is JournalEvent.ConflictResolved -> base.copy(
            kind = JournalKindUi.Conflict,
            title = UiText.of(R.string.journal_conflict_resolved, event.path.fileName()),
            details = listOfNotNull(
                when (event.outcome) {
                    JournalEvent.ConflictResolved.Outcome.KeptLocal -> UiText.of(R.string.journal_conflict_kept_local)
                    JournalEvent.ConflictResolved.Outcome.KeptRemote ->
                        UiText.of(R.string.journal_conflict_kept_remote, names.peer(event.deviceId))

                    JournalEvent.ConflictResolved.Outcome.KeptBoth -> UiText.of(R.string.journal_conflict_kept_both)
                },
                UiText.of(R.string.journal_conflict_newest_wins)
                    .takeIf { event.decidedBy == JournalEvent.ConflictResolved.DecidedBy.LastWriteWins },
            ),
        )

        is JournalEvent.ConflictDecisionDropped -> base.copy(
            kind = JournalKindUi.Conflict,
            title = UiText.of(R.string.journal_conflict_dropped, event.path.fileName()),
            details = listOf(
                UiText.of(
                    when (event.reason) {
                        JournalEvent.ConflictDecisionDropped.Reason.SideChanged -> R.string.journal_conflict_dropped_changed
                        JournalEvent.ConflictDecisionDropped.Reason.ChoiceUnavailable -> R.string.journal_conflict_dropped_unavailable
                        JournalEvent.ConflictDecisionDropped.Reason.NoLongerConflicts -> R.string.journal_conflict_dropped_settled
                    }
                )
            ),
        )

        is JournalEvent.ClockSkewed -> base.copy(
            kind = JournalKindUi.Clock,
            title = UiText.of(R.string.journal_clock_skewed, names.peer(event.deviceId)),
            details = listOf(
                UiText.of(
                    if (event.offsetMs > 0) R.string.journal_clock_ahead else R.string.journal_clock_behind,
                    DateUtils.formatElapsedTime(abs(event.offsetMs) / 1000),
                ),
                UiText.of(R.string.journal_clock_hint),
            ),
        )

        is JournalEvent.IncompatibleDictionary -> base.copy(
            kind = JournalKindUi.Link,
            title = UiText.of(R.string.journal_dictionary_incompatible, names.peer(event.deviceId)),
            details = listOf(UiText.of(R.string.journal_dictionary_versions, event.localVersion, event.remoteVersion)),
        )

        is JournalEvent.ConnectionRefused -> base.copy(
            kind = JournalKindUi.Link,
            title = UiText.of(R.string.journal_connection_refused, names.peer(event.deviceId)),
            details = listOf(
                UiText.of(
                    when (event.reason) {
                        JournalEvent.ConnectionRefused.Reason.AuthenticationRejected -> R.string.journal_connection_auth
                        JournalEvent.ConnectionRefused.Reason.IdentityMismatch -> R.string.journal_connection_identity
                        JournalEvent.ConnectionRefused.Reason.HandshakeFailed -> R.string.journal_connection_handshake
                    }
                )
            ),
        )

        is JournalEvent.DevicePaired -> base.copy(
            kind = JournalKindUi.Device,
            title = UiText.of(R.string.journal_device_paired, event.name),
            details = listOf(UiText.of(event.method.labelRes)),
        )

        is JournalEvent.DeviceForgotten -> base.copy(
            kind = JournalKindUi.Device,
            title = UiText.of(R.string.journal_device_forgotten, event.name),
        )

        is JournalEvent.SourceAdded -> base.copy(
            kind = JournalKindUi.Source,
            title = when (event.role) {
                SourceEntry.Role.Initiator -> UiText.of(R.string.journal_source_added, event.label)
                SourceEntry.Role.Follower -> UiText.of(R.string.journal_source_hosted, event.label, names.peer(event.deviceId))
            },
            details = listOf(UiText.of(event.mode.toUi().titleRes)),
        )

        is JournalEvent.SourceRemoved -> base.copy(
            kind = JournalKindUi.Source,
            title = UiText.of(R.string.journal_source_removed, event.label),
        )

        is JournalEvent.SourceRequested -> base.copy(
            kind = JournalKindUi.Source,
            title = UiText.of(R.string.journal_source_requested, names.peer(event.deviceId), event.label),
            details = listOf(UiText.of(event.mode.toUi().titleRes)),
        )

        is JournalEvent.SourceRequestRejected -> base.copy(
            kind = JournalKindUi.Source,
            title = UiText.of(R.string.journal_source_request_rejected, event.label, names.peer(event.deviceId)),
        )

        is JournalEvent.SourceAcceptedByPeer -> base.copy(
            kind = JournalKindUi.Source,
            title = UiText.of(R.string.journal_source_accepted, names.peer(event.deviceId), event.label),
        )

        is JournalEvent.SourceDisabledByPeer -> base.copy(
            title = UiText.of(
                when (event.reason) {
                    JournalEvent.SourceDisabledByPeer.Reason.Refused -> R.string.journal_source_refused
                    JournalEvent.SourceDisabledByPeer.Reason.Removed -> R.string.journal_source_dropped
                },
                names.peer(event.deviceId),
                event.label,
            ),
            details = listOf(UiText.of(R.string.journal_source_disabled)),
        )

        is JournalEvent.SourceChanged -> base.copy(
            kind = JournalKindUi.Settings,
            title = when (event.by) {
                JournalEvent.SourceChanged.ChangedBy.User -> UiText.of(R.string.journal_source_changed, event.label)
                JournalEvent.SourceChanged.ChangedBy.Peer ->
                    UiText.of(R.string.journal_source_changed_by_peer, event.label, names.peer(event.deviceId))
            },
            details = event.changes.map { UiText.of(it.labelRes) },
        )

        is JournalEvent.EncryptionMigrated -> base.copy(
            kind = JournalKindUi.Encryption,
            title = UiText.of(
                when (event.target) {
                    JournalEvent.EncryptionTarget.Encrypted -> R.string.journal_encryption_encrypted
                    JournalEvent.EncryptionTarget.Decrypted -> R.string.journal_encryption_decrypted
                },
                names.source(event.sourceId),
            ),
            details = listOf(UiText.of(R.string.journal_files_count, event.files)),
        )

        is JournalEvent.EncryptionIncomplete -> base.copy(
            title = UiText.of(
                when (event.target) {
                    JournalEvent.EncryptionTarget.Encrypted -> R.string.journal_encryption_incomplete
                    JournalEvent.EncryptionTarget.Decrypted -> R.string.journal_decryption_incomplete
                },
                names.source(event.sourceId),
            ),
            details = listOf(UiText.of(R.string.journal_encryption_failed_files, event.failed)),
        )

        is JournalEvent.OneShotFinished -> base.copy(
            kind = if (event.outcome == JournalEvent.OneShotFinished.Outcome.Completed) {
                JournalKindUi.Transfer
            } else {
                JournalKindUi.Failed
            },
            title = UiText.of(event.titleRes, event.peerName),
            details = listOf(UiText.of(R.string.journal_files_count, event.files)),
        )
    }
}

private class Names(
    private val peers: Map<String, PeerUi>,
    private val labels: Map<String, String>,
) {
    fun peer(deviceId: String): String = peers.peerOf(deviceId).name

    fun source(sourceId: String): UiText =
        labels[sourceId]?.let(UiText::Text) ?: UiText.of(R.string.journal_unknown_source)
}

private val JournalEvent.group: JournalGroupUi
    get() = when (this) {
        is JournalEvent.PassCompleted,
        is JournalEvent.FileFetched,
            -> JournalGroupUi.Sync

        is JournalEvent.PassFailed,
        is JournalEvent.PeerPassFailed,
        is JournalEvent.SealedFilesUnreadable,
        is JournalEvent.ClockSkewed,
        is JournalEvent.IncompatibleDictionary,
        is JournalEvent.ConnectionRefused,
            -> JournalGroupUi.Problems

        is JournalEvent.ConflictHeld,
        is JournalEvent.ConflictResolved,
        is JournalEvent.ConflictDecisionDropped,
            -> JournalGroupUi.Conflicts

        is JournalEvent.SourceAdded,
        is JournalEvent.SourceRemoved,
        is JournalEvent.SourceRequested,
        is JournalEvent.SourceRequestRejected,
        is JournalEvent.SourceAcceptedByPeer,
        is JournalEvent.SourceDisabledByPeer,
        is JournalEvent.SourceChanged,
            -> JournalGroupUi.Sources

        is JournalEvent.EncryptionMigrated,
        is JournalEvent.EncryptionIncomplete,
            -> JournalGroupUi.Encryption

        is JournalEvent.DevicePaired,
        is JournalEvent.DeviceForgotten,
            -> JournalGroupUi.Devices

        is JournalEvent.OneShotFinished -> JournalGroupUi.Transfers
    }

private val JournalEvent.SourceChanged.Change.labelRes: Int
    get() = when (this) {
        JournalEvent.SourceChanged.Change.ModeSettings -> R.string.journal_change_mode
        JournalEvent.SourceChanged.Change.DeviceConstraints -> R.string.journal_change_constraints
        JournalEvent.SourceChanged.Change.FileLimits -> R.string.journal_change_limits
        JournalEvent.SourceChanged.Change.Encryption -> R.string.journal_change_encryption
    }

private val SyncMode.Type.offloads: Boolean
    get() = this == SyncMode.Type.Offload || this == SyncMode.Type.Host

private val JournalEvent.PassCompleted.kind: JournalKindUi
    get() = when {
        mode.offloads -> JournalKindUi.Offloaded
        tally.sent + tally.received + tally.moved == 0 && tally.deletedHere + tally.deletedOnPeer > 0 ->
            JournalKindUi.Deleted

        else -> JournalKindUi.Synced
    }

private fun PassTally.details(peerName: String): List<UiText> = buildList {
    if (sent > 0) add(UiText.of(R.string.journal_tally_sent, sent))
    if (received > 0) add(UiText.of(R.string.journal_tally_received, received))
    if (deletedHere > 0) add(UiText.of(R.string.journal_tally_deleted_here, deletedHere))
    if (deletedOnPeer > 0) add(UiText.of(R.string.journal_tally_deleted_on_peer, peerName, deletedOnPeer))
    if (moved > 0) add(UiText.of(R.string.journal_tally_moved, moved))
    if (evicted > 0) add(UiText.of(R.string.journal_tally_evicted, evicted))
    if (skipped > 0) add(UiText.of(R.string.journal_tally_skipped, skipped))
}

private val JournalEvent.OneShotFinished.titleRes: Int
    get() = when (direction) {
        JournalEvent.OneShotFinished.Direction.Outgoing -> when (outcome) {
            JournalEvent.OneShotFinished.Outcome.Completed -> R.string.journal_oneshot_sent
            JournalEvent.OneShotFinished.Outcome.Declined -> R.string.journal_oneshot_sent_declined
            JournalEvent.OneShotFinished.Outcome.Cancelled -> R.string.journal_oneshot_sent_cancelled
            JournalEvent.OneShotFinished.Outcome.Failed -> R.string.journal_oneshot_sent_failed
        }

        JournalEvent.OneShotFinished.Direction.Incoming -> when (outcome) {
            JournalEvent.OneShotFinished.Outcome.Completed -> R.string.journal_oneshot_received
            JournalEvent.OneShotFinished.Outcome.Declined -> R.string.journal_oneshot_received_declined
            JournalEvent.OneShotFinished.Outcome.Cancelled -> R.string.journal_oneshot_received_cancelled
            JournalEvent.OneShotFinished.Outcome.Failed -> R.string.journal_oneshot_received_failed
        }
    }
