package com.fserver.app.presentation.screens.settings.transfers.model

import androidx.compose.runtime.Immutable
import com.fserver.common.model.FileSize
import com.fserver.core.files.SourceLocation
import kotlin.time.Instant

@Immutable
data class TransfersState(
    /** Null until read: the default depends on what the OS grants. */
    val destination: SourceLocation.Hostable? = null,
    val autoAccept: Boolean = false,
    /** Newest first. */
    val transfers: List<TransferUi> = emptyList(),
) {
    val hasFinished: Boolean get() = transfers.any { it.status.isFinished }

    @Immutable
    data class TransferUi(
        val id: String,
        val peerName: String,
        val outgoing: Boolean,
        val status: StatusUi,
        val files: List<FileUi>,
        val totalSize: FileSize,
        /** Set while bytes move; null means nothing to draw. */
        val progress: Float?,
        val createdAt: Instant,
    ) {
        val canCancel: Boolean get() = !status.isFinished
        val canRetry: Boolean get() = outgoing && !status.isFinished
    }

    @Immutable
    data class FileUi(
        val index: Int,
        val name: String,
        val size: FileSize,
        /** Where a received file was written; null for anything not openable here. */
        val openLocator: String?,
    )

    sealed interface StatusUi {
        /** Outgoing: the peer has not answered. Incoming: this side has not. */
        data object Pending : StatusUi
        data object Active : StatusUi
        data object Completed : StatusUi
        data object Declined : StatusUi
        data object Cancelled : StatusUi
        data class Failed(val reason: String) : StatusUi

        val isFinished: Boolean get() = this != Pending && this != Active
    }
}
