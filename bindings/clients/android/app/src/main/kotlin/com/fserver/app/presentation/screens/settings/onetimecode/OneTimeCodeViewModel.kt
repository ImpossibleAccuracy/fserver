package com.fserver.app.presentation.screens.settings.onetimecode

import com.fserver.app.util.stateInScreen
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.fserver.app.presentation.screens.settings.onetimecode.model.OneTimeCodeIntent
import com.fserver.app.presentation.screens.settings.onetimecode.model.OneTimeCodeState
import com.fserver.core.network.auth.PairingCodes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant
import com.fserver.core.network.auth.PairingCodeState as CodeState

@OptIn(ExperimentalCoroutinesApi::class)
class OneTimeCodeViewModel(
    private val pairingCodes: PairingCodes,
) : ViewModel() {

    val state: StateFlow<OneTimeCodeState> = pairingCodes.state
        .flatMapLatest { code ->
            when (code) {
                is CodeState.Active -> countdown(code.expiresAt).map { code.toPresentation(it) }
                else -> flowOf(code.toPresentation())
            }
        }
        .stateInScreen(viewModelScope, OneTimeCodeState())

    init {
        if (pairingCodes.state.value !is CodeState.Active) pairingCodes.issue()
    }

    fun onIntent(intent: OneTimeCodeIntent) {
        when (intent) {
            OneTimeCodeIntent.NewCode -> pairingCodes.issue()
        }
    }

    override fun onCleared() {
        pairingCodes.revoke()
    }

    private fun countdown(deadline: Instant): Flow<Duration> = flow {
        while (true) {
            val left = deadline - Clock.System.now()
            if (left <= Duration.ZERO) {
                emit(Duration.ZERO)
                return@flow
            }
            val shown = left.inWholeSeconds.seconds
            emit(if (shown < left) shown + 1.seconds else shown)
            delay(left - shown + 1.milliseconds)
        }
    }

    private fun CodeState.Active.toPresentation(remaining: Duration) = OneTimeCodeState(
        status = OneTimeCodeState.Status.Active,
        codeGroups = code.chunked(3),
        remaining = remaining,
    )

    private fun CodeState.toPresentation() = OneTimeCodeState(
        status = when (this) {
            is CodeState.Active -> OneTimeCodeState.Status.Active
            CodeState.Idle -> OneTimeCodeState.Status.Idle
            CodeState.Expired -> OneTimeCodeState.Status.Expired
            CodeState.Spent -> OneTimeCodeState.Status.Spent
            CodeState.Used -> OneTimeCodeState.Status.Used
        },
    )
}
