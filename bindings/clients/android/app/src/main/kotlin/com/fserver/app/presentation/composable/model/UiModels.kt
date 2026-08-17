package com.fserver.app.presentation.composable.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.designkit.DkCheckState

/**
 * Presentation-layer models for the MVP screens.
 *
 * These are UI shapes only — they carry pre-formatted labels, not domain values. When
 * `:core` starts producing real discovery/index/transfer state, a mapper feeds these;
 * the screens do not move.
 */

enum class FileKindUi { Folder, Image, Video, Audio, Document, Other }

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

    data class Running(
        override val id: String,
        override val fileName: String,
        val progress: Float,
        val transferredLabel: String,
        val totalLabel: String,
        val speedLabel: String,
        val etaLabel: String,
    ) : TransferUi

    data class Queued(
        override val id: String,
        override val fileName: String,
    ) : TransferUi

    /** Paused by the user. Same resumable position as an interruption, different cause. */
    data class Paused(
        override val id: String,
        override val fileName: String,
        val progress: Float,
        val transferredLabel: String,
        val totalLabel: String,
    ) : TransferUi

    /**
     * A dropped connection is an ordinary state here, not an error: the transfer resumes
     * from where it stopped and the hash is checked at the end.
     */
    data class Interrupted(
        override val id: String,
        override val fileName: String,
        val stoppedAtPercent: Int,
    ) : TransferUi

    data class Completed(
        override val id: String,
        override val fileName: String,
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
