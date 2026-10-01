package com.fserver.app.presentation.share.model

import androidx.compose.runtime.Immutable
import com.fserver.app.presentation.composable.model.PeerUi
import com.fserver.app.presentation.shared.error.AppError

@Immutable
data class ShareState(
    val fileCount: Int = 0,
    /** Paired devices, and any device with a session up. */
    val devices: List<PeerUi> = emptyList(),
    /** Copying the shared files while their grant lasts. */
    val sending: Boolean = false,
    val error: AppError? = null,
)
