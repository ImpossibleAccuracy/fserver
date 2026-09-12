package com.fserver.app.presentation.composable.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.designkit.DkCheckState
import com.fserver.common.model.FileSize
import com.fserver.core.sync.progress.FileTransfer
import kotlin.time.Duration
import kotlin.time.Instant

/**
 * Presentation-layer models for the MVP screens.
 *
 * These are UI shapes only. Values that need a locale or a Context to read — sizes, dates,
 * durations — stay domain values here and are formatted where they are drawn.
 */

enum class FileKindUi {
    Folder, Image, Video, Audio, Document, Other;

    val isMedia: Boolean
        get() = this == Image || this == Video || this == Audio
}

/**
 * Where the bytes are. One feed lists every source, so the trailing marker is the only thing
 * telling a local file from an offloaded one from a file living on someone else's device.
 */
enum class FileAvailabilityUi {
    /** Here, nothing to fetch. */
    OnDevice,

    /** Freed locally; the copy sits on the device that took it. */
    Offloaded,

    /** Lives on another device and is not mirrored here. */
    OnPeer,

    /** Not here yet; a tap fetches it. */
    OnServer,
}

@Immutable
data class FileUi(
    val id: String,
    val name: String,
    val kind: FileKindUi,
    val size: FileSize? = null,
    val modifiedAt: Instant? = null,
    val childCount: Int? = null,
    val availability: FileAvailabilityUi = FileAvailabilityUi.OnServer,
    val extensionLabel: String? = null,
    val durationLabel: String? = null,
)

@Immutable
data class TreeNodeUi(
    val id: String,
    val name: String,
    val depth: Int,
    val isFolder: Boolean,
    val expanded: Boolean = false,
    val childCountLabel: String? = null,
    val availability: FileAvailabilityUi? = null,
)

enum class FilesViewModeUi { List, Grid, Tree }

@Immutable
sealed interface TransferUi {
    val id: String
    val fileName: String

    /** Which way the bytes go. The row draws it, and the two directions never merge. */
    val direction: FileTransfer.Direction

    data class Running(
        override val id: String,
        override val fileName: String,
        override val direction: FileTransfer.Direction,
        /** `null` while the size is unknown, so the bar runs indeterminate. */
        val progress: Float?,
        val transferred: FileSize,
        val total: FileSize,
        val bytesPerSecond: Long?,
        val eta: Duration?,
    ) : TransferUi

    /**
     * A whole pass, as one line. The activity feed shows the pass rather than its files —
     * thirty rows that all say the same thing are not thirty pieces of information.
     */
    data class Batch(
        override val id: String,
        override val fileName: String,
        override val direction: FileTransfer.Direction,
        val peerName: String,
        val doneCount: Int,
        val totalCount: Int,
        /** `null` while the size is unknown, so the bar runs indeterminate. */
        val progress: Float?,
        val bytesPerSecond: Long?,
    ) : TransferUi

    /** Planned by the current pass, nothing sent yet. */
    data class Queued(
        override val id: String,
        override val fileName: String,
        override val direction: FileTransfer.Direction,
    ) : TransferUi

    /**
     * Stopped short. Not an error state: the next pass re-plans the file from wherever the
     * index got to, and the hash is checked at the end either way.
     */
    data class Interrupted(
        override val id: String,
        override val fileName: String,
        override val direction: FileTransfer.Direction,
        val stoppedAtPercent: Int,
    ) : TransferUi

    data class Completed(
        override val id: String,
        override val fileName: String,
        override val direction: FileTransfer.Direction,
    ) : TransferUi
}

/**
 * One connection check. Title and detail are resource ids because the detail line is
 * fixed copy in the MVP; once checks run for real the detail becomes a formatted value.
 */
@Immutable
data class DiagnosticCheckUi(
    val id: String,
    @param:StringRes val titleRes: Int,
    @param:StringRes val detailRes: Int,
    val state: DkCheckState,
)
