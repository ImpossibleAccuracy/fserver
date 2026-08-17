package com.fserver.core.network.auth.impl

import com.fserver.core.network.device.model.PendingConfirmation
import com.fserver.net.security.PeerAuthenticator
import com.fserver.net.security.trust.TrustPrompt
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Implementation of [PeerAuthenticator] that requires user interaction to verify a peer.
 */
internal class InteractivePeerAuthenticator : PeerAuthenticator {
    private val lock = Mutex()
    private val _pending = MutableStateFlow<PendingConfirmation?>(null)
    val pending: StateFlow<PendingConfirmation?> = _pending

    private var answer: CompletableDeferred<Boolean>? = null

    override suspend fun verify(
        prompt: TrustPrompt,
    ): PeerAuthenticator.Decision = lock.withLock {
        val deferred = CompletableDeferred<Boolean>()
        answer = deferred
        _pending.value = PendingConfirmation(
            deviceId = prompt.candidate.deviceId,
            codeGroups = prompt.confirmationCode?.let {
                val trimmed = it.trim().replace(" ", "")
                if (trimmed.length < GroupSize * 2) listOf(trimmed)
                else trimmed.chunked(GroupSize)
            }.orEmpty(),
        )

        val accepted = try {
            deferred.await()
        } finally {
            _pending.value = null
            answer = null
        }

        if (accepted) {
            PeerAuthenticator.Decision.Trust
        } else {
            PeerAuthenticator.Decision.Reject("rejected by user")
        }
    }

    /** Answers whichever peer is currently in [pending]. No-op if there is none. */
    fun resolve(accept: Boolean) {
        answer?.complete(accept)
    }

    companion object {
        const val GroupSize = 4
    }
}
