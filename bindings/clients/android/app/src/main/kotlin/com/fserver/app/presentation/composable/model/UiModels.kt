package com.fserver.app.presentation.composable.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.designkit.DkCheckState
import com.fserver.common.model.FileSize
import com.fserver.core.sync.progress.FileTransfer
import kotlin.time.Duration

/**
 * Presentation-layer models for the MVP screens.
 *
 * These are UI shapes only — they carry pre-formatted labels, not domain values. When
 * `:core` starts producing real discovery/index/transfer state, a mapper feeds these;
 * the screens do not move.
 */

enum class FileKindUi {
    @Deprecated("folder is not a file kind, delete")
    Folder,
    Image, Video, Audio, Document, Other;

    val isMedia: Boolean
        get() = this == Image || this == Video
}

/** Whether the bytes are here or still on the server. Drives the row's trailing marker. */
enum class FileAvailabilityUi { OnServer, OnDevice }

@Immutable
data class FileUi(
    val id: String,
    val name: String,
    val kind: FileKindUi,
    val sizeLabel: String? = null,
    val dateLabel: String? = null,
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
