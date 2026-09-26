package com.fserver.app.presentation.composable.model

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.graphics.vector.ImageVector
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.model.SyncMode

/** Which way a link's files go, seen from this device. */
enum class LinkDirectionUi(val icon: ImageVector) {
    Outgoing(Icons.AutoMirrored.Filled.ArrowForward),
    Incoming(Icons.AutoMirrored.Filled.ArrowBack),
    Mirror(Icons.Default.SwapHoriz),
}

fun SourceEntry.direction(): LinkDirectionUi = when {
    syncMode is SyncMode.Mirror -> LinkDirectionUi.Mirror
    role == SourceEntry.Role.Initiator -> LinkDirectionUi.Outgoing
    else -> LinkDirectionUi.Incoming
}
