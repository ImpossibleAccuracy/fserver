package com.fserver.app.presentation.screens.settings.onetimecode.model

import androidx.compose.runtime.Immutable
import kotlin.time.Duration

@Immutable
data class OneTimeCodeState(
    val status: Status = Status.Idle,
    val codeGroups: List<String> = emptyList(),
    val remaining: Duration = Duration.ZERO,
) {
    enum class Status { Idle, Active, Expired, Spent, Used }

    val isActive: Boolean
        get() = status == Status.Active
}
