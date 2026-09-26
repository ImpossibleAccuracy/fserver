package com.fserver.core.network.auth.impl

import com.fserver.core.di.BackgroundScope
import com.fserver.core.network.auth.PairingCodeState
import com.fserver.core.network.auth.PairingCodes
import com.fserver.core.util.TimeProvider
import com.fserver.net.security.auth.pake.OneTimeCodeSource
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.security.SecureRandom
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes

/** [PairingCodes] for the UI, [OneTimeCodeSource] for the auth method - the same live code. */
internal class PairingCodesImpl(
    private val timeProvider: TimeProvider,
    private val scope: BackgroundScope,
    private val ttl: Duration = DefaultTtl,
) : PairingCodes, OneTimeCodeSource {
    private val random = SecureRandom()
    private val _state = MutableStateFlow<PairingCodeState>(PairingCodeState.Idle)
    override val state: StateFlow<PairingCodeState> = _state.asStateFlow()

    private var expiry: Job? = null

    override fun issue() {
        val active = PairingCodeState.Active(
            code = (0 until CodeLength).joinToString("") { random.nextInt(10).toString() },
            expiresAt = timeProvider.now() + ttl,
        )
        _state.value = active

        expiry?.cancel()
        expiry = scope.launch {
            delay(ttl)
            _state.compareAndSet(active, PairingCodeState.Expired)
        }
    }

    override fun revoke() {
        expiry?.cancel()
        _state.value = PairingCodeState.Idle
    }

    override suspend fun take(): String? {
        while (true) {
            val current = _state.value as? PairingCodeState.Active ?: return null

            if (timeProvider.now() >= current.expiresAt) {
                _state.compareAndSet(current, PairingCodeState.Expired)
                return null
            }

            if (_state.compareAndSet(current, PairingCodeState.Spent)) return current.code
        }
    }

    override fun onUsed() {
        _state.compareAndSet(PairingCodeState.Spent, PairingCodeState.Used)
    }

    companion object {
        const val CodeLength = 6
        val DefaultTtl = 2.minutes
    }
}
