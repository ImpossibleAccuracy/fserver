package com.fserver.core.data.security

import com.fserver.core.domain.model.connection.PendingConfirmation
import com.fserver.net.security.PeerAuthenticator
import com.fserver.net.security.identity.PeerIdentity
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Э9 scaffold: gates every peer on an explicit user comparison instead of trusting on sight.
 * No real PAKE yet - the code compared is whatever the [AuthMethod][com.fserver.net.security.auth.AuthMethod]
 * derived (a fingerprint for confirm-dh, digits for Nearby's SAS) - but this is the first thing
 * that actually asks, rather than defaulting every verdict to [Decision.Trust].
 *
 * One comparison in flight at a time: a second peer knocking mid-comparison queues behind [lock]
 * rather than clobbering [pending].
 */
internal class InteractivePeerAuthenticator : PeerAuthenticator {
    private val lock = Mutex()
    private val _pending = MutableStateFlow<PendingConfirmation?>(null)
    val pending: StateFlow<PendingConfirmation?> = _pending

    private var answer: CompletableDeferred<Boolean>? = null

    override suspend fun verify(
        candidate: PeerIdentity,
        confirmationCode: String?,
    ): PeerAuthenticator.Decision = lock.withLock {
        val deferred = CompletableDeferred<Boolean>()
        answer = deferred
        _pending.value = PendingConfirmation(
            deviceId = candidate.deviceId,
            codeGroups = confirmationCode?.let {
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
