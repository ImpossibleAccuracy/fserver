package com.fserver.app.presentation.screens.settings.details.model

import androidx.annotation.StringRes

data class DeviceDetailsState(
    val name: String = "",
    val isConnected: Boolean = false,
    val address: String? = null,
    /** Pre-split groups for `DkFingerprintBlock`; empty until a handshake has actually run. */
    val fingerprintGroups: List<String> = emptyList(),
    val lastSeen: String? = null,
    @param:StringRes val methodLabel: Int? = null,
    val protocolVersion: Int? = null,
    /** False once the record is gone — the screen then has nothing left to forget. */
    val isTrusted: Boolean = false,
    /** True while a route recorded by an earlier connection is still on disk to reconnect through. */
    val isRouteKnown: Boolean = false,
)
