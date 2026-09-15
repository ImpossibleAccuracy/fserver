package com.fserver.core.lifecycle.network

import com.fserver.core.di.BackgroundScope
import com.fserver.core.store.FServerStorage
import com.fserver.core.sync.model.SourceEntry
import com.fserver.net.connection.IncomingConnectionsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import timber.log.Timber
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi

/**
 * Answers a device that dials in, without putting the question in front of the user, when that
 * device have any active source. Everyone else is passed on and asked about as before.
 *
 * **Nothing here authorizes anything.** Who is knocking is not known until
 * the handshake proves it - the request arrives before authentication - so the id below is a guess,
 * read off what the peer advertised or off the address it came from.
 * All it decides is whether to ask the user.
 * A peer that guessed its way past this still has to authenticate against the pinned key, and one that
 * cannot get nowhere; a key that does not match the record puts the trust prompt in front of the
 * user exactly as it would have anyway.
 */
@OptIn(ExperimentalAtomicApi::class)
internal class AutoAcceptCoordinator(
    private val storage: FServerStorage,
    private val backgroundScope: BackgroundScope,
) {
    private val isEnabled = AtomicBoolean(false)

    /** Idempotent, and takes effect on the next request rather than on anything already asked. */
    fun start() {
        isEnabled.store(true)
    }

    /** Back to asking about everything. [start] works again afterward. */
    fun stop() {
        isEnabled.store(false)
    }

    /**
     * True when [request] has been taken over and must not be put in front of the user. The answer
     * itself is given off this call: a handshake takes seconds, and the requests behind this one
     * must not wait on it.
     */
    suspend fun handles(request: IncomingConnectionsManager.IncomingRequest): Boolean {
        if (!isEnabled.load()) return false

        val paired = pairedDeviceIds()
        if (paired.isEmpty()) return false

        backgroundScope.launch {
            Timber.i("Device dialled in with sources registered against it, accepting")

            request.accept().onFailure { Timber.w(it, "could not auto-accept") }
        }

        return true
    }

    /** Trusted, and with a source of ours registered against them - the same pair auto-sync runs on. */
    private suspend fun pairedDeviceIds(): Set<String> {
        val trusted = storage.trust.knownDeviceIds.first()
        if (trusted.isEmpty()) return emptySet()

        return storage.sources.all()
            .filter { it.status !is SourceEntry.Status.Disabled && it.deviceId in trusted }
            .mapTo(mutableSetOf(), SourceEntry::deviceId)
    }
}
