package com.fserver.net.connection.impl

import com.fserver.net.NetworkException
import com.fserver.net.config.ConfigAware
import com.fserver.net.config.NetworkConfig
import com.fserver.net.config.NetworkConfigHolder
import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.connection.HandshakeProfile
import com.fserver.net.connection.PeerRef
import com.fserver.net.security.auth.permits
import com.fserver.net.session.CloseReason
import com.fserver.net.session.PeerSession
import com.fserver.net.session.PeerSessionImpl
import com.fserver.net.session.SessionLink
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class ConnectionsHolder<M : Any>(
    private val configHolder: NetworkConfigHolder<M>,
    private val scope: CoroutineScope,
) : ConfigAware {
    private val config: NetworkConfig<M> get() = configHolder.current

    // One session per deviceId, whatever route it came in on.
    private val registry = MutableStateFlow<Map<String, PeerSessionImpl<M>>>(emptyMap())
    val sessions get() = registry.asStateFlow()

    // Outlives the session it came from: pairing looks at a device it has deliberately hung up on.
    private val profileRegistry = MutableStateFlow<Map<String, HandshakeProfile>>(emptyMap())
    val profiles get() = profileRegistry.asStateFlow()
    private val registryLock = Mutex()

    fun sessionFor(deviceId: String): PeerSession<M>? = registry.value[deviceId]

    /**
     * A session outlives the config it was built under only for as long as that config would still
     * admit it: its transport dropping out, or the method that authenticated it, ends it.
     * Anything still permitted is left alone - a reload is not a reason to interrupt a working session.
     *
     * Sessions between links count too. One re-establishing itself would re-handshake under the
     * new config and fail there, but only after burning its whole reconnect budget first.
     */
    override suspend fun onConfigChanged(old: NetworkConfig<*>, new: NetworkConfig<*>) {
        val barred = registry.value.values.filterNot {
            new.permits(it.route.transport, it.authMethodId)
        }

        // At once, not one after another: each close flushes a CLOSE frame on its own deadline,
        // and a reload should not wait for those end to end.
        coroutineScope {
            barred.forEach { session ->
                launch { session.close(CloseReason.Local("no longer permitted by config")) }
            }
        }
    }

    /** Register a new session, or return an existing one if it raced in first. */
    suspend fun register(
        route: PeerRef,
        link: SessionLink,
        policy: ConnectionPolicy,
        relink: (suspend () -> SessionLink)?,
    ): PeerSession<M> = registryLock.withLock {
        val deviceId = link.negotiated.peer.deviceId

        // Double-check that the config still permits this session
        val method = link.negotiated.authMethodId
        if (!config.permits(route.transport, method)) {
            link.secure.close()
            throw NetworkException.Transport(
                "$method over ${route.transport.value} is no longer permitted by config"
            )
        }

        // Only what this side dialed: an inbound socket's remote address is not a route back.
        if (relink != null) rememberProfile(route, link)

        registry.value[deviceId]?.let { existing ->
            val finished =
                existing.state.value.let { it is PeerSession.State.Closed || it is PeerSession.State.Failed }
            if (!finished) {
                // Raced with another caller; keep the first session and drop the spare link.
                link.secure.close()
                return@withLock existing
            }
            // A session that already died has not necessarily been unregistered yet.
            forgetDevice(deviceId)
        }

        if (registry.value.size >= policy.sessionConfig.maxSessions) {
            link.secure.close()
            throw NetworkException.Transport("session limit ${policy.sessionConfig.maxSessions} reached")
        }

        val session = PeerSessionImpl(
            route = route,
            negotiated = link.negotiated,
            codec = config.dictionary.codec,
            policy = policy,
            logger = config.logger,
            parentScope = scope,
            relink = relink,
            onTerminated = { finished -> forgetDevice(finished.negotiated.peer.deviceId) },
        )

        registry.update { it + (deviceId to session) }
        session.start(link)
        session
    }

    /**
     * Files the handshake result under the id the peer just claimed. [PeerRef.Companion.build] dials with a
     * blank device id, so the stored route is re-stamped - otherwise nothing could dial it again.
     */
    private fun rememberProfile(route: PeerRef, link: SessionLink): HandshakeProfile {
        val deviceId = link.negotiated.peer.deviceId
        val profile = HandshakeProfile(
            identity = link.negotiated.peer,
            negotiated = link.negotiated,
            route = route.copy(deviceId = deviceId),
        )
        profileRegistry.update { it + (deviceId to profile) }
        return profile
    }

    /** Removes a session and its profile from the registry. */
    fun forgetDevice(deviceId: String) {
        registry.update { it - deviceId }
        profileRegistry.update { it - deviceId }
    }

    /** Clears all sessions and profiles without closing them. */
    fun reset() {
        registry.update { emptyMap() }
        profileRegistry.update { emptyMap() }
    }
}
