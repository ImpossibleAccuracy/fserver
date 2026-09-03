package com.fserver.app.presentation.screens.request.done.model

import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable

@Immutable
data class SyncRequestDoneState(
    val label: String = "",
    val deviceName: String = "",
    @param:StringRes val modeRes: Int? = null,
    val locationLabel: String? = null,
    @param:StringRes val locationRes: Int? = null,
)
