package com.fserver.net.connection.impl

import com.fserver.common.exception.NetworkException
import com.fserver.net.config.ConfigAware
import com.fserver.net.config.NetworkConfig
import com.fserver.net.config.NetworkConfigHolder
import com.fserver.net.connection.IncomingConnectionsManager
import com.fserver.net.connection.PeerRef
import com.fserver.net.connection.throttle.HandshakeSource
import com.fserver.net.connection.throttle.HandshakeThrottle
import com.fserver.net.handshake.FramePump
import com.fserver.net.handshake.HandshakeNegotiator
import com.fserver.net.session.CloseReason
import com.fserver.net.session.PeerSession
import com.fserver.net.spi.DiscoveredEndpoint
import com.fserver.net.spi.SpiId
import com.fserver.net.spi.Transport
import com.fserver.common.utils.runCatchingCancellable
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

internal class IncomingConnectionsManagerImpl<M : Any>(
    private val configHolder: NetworkConfigHolder<M>,
    private val negotiator: HandshakeNegotiator,
    private val connectionsHolder: ConnectionsHolder<M>,
    private val scope: CoroutineScope,
) : IncomingConnectionsManager<M>, ConfigAware {
    private val config: NetworkConfig<M> get() = configHolder.current

    // Throttle doesn't care about config reload.
    private val throttle = HandshakeThrottle(configHolder.current.policy)

    override val sessions: StateFlow<List<PeerSession<M>>> = connectionsHolder.sessions
        .map { it.values.toList() }
        .stateIn(scope, SharingStarted.Eagerly, emptyList())

    // A SharedFlow drops emissions made while nobody is collecting - a handshake landing right
    // before the host subscribes would vanish with no signal. A channel queues instead.
    private val requests = Channel<IncomingConnectionsManager.IncomingRequest>(Channel.BUFFERED)
    override val incoming: Flow<IncomingConnectionsManager.IncomingRequest> =
        requests.receiveAsFlow()

    // One job per listening transport, keyed so a reload only disturbs the transports that
    // actually changed. Re-listening wholesale would unbind and rebind sockets that were working,
    // dropping whatever was mid-accept on them.
    private val listeners = ConcurrentHashMap<SpiId, Job>()

    init {
        startListeners(configHolder.current.transports)
    }

    override fun session(deviceId: String): PeerSession<M>? =
        connectionsHolder.sessionFor(deviceId)

    /**
     * Takes this node off the transports the new config dropped and onto the ones it added. The
     * transports that stayed keep the listener they already had.
     */
    override suspend fun onConfigChanged(old: NetworkConfig<*>, new: NetworkConfig<*>) {
        stopListeners(keep = new.transports.mapTo(mutableSetOf()) { it.id })
        startListeners(new.transports)
    }

    suspend fun shutdown() {
        stopListeners(keep = emptySet())
        connectionsHolder.sessions.value.values
            .forEach { it.close(CloseReason.Local("node closed")) }
    }

    private fun startListeners(transports: List<Transport>) {
        transports.forEach { transport ->
            val listener = transport.listener ?: return@forEach
            listeners.compute(transport.id) { _, existing ->
                existing?.takeIf { it.isActive }
                    ?: listen(transport, listener)
            }
        }
    }

    private suspend fun stopListeners(keep: Set<SpiId>) {
        listeners.keys
            .filterNot { it in keep }
            .forEach { listeners.remove(it)?.cancelAndJoin() }
    }

    private fun listen(transport: Transport, listener: Transport.Listener): Job = scope.launch {
        try {
            listener.listen().collect { connection ->
                // One coroutine each per connection
                launch {
                    admit(owner = transport, connection = connection)?.let { requests.send(it) }
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            // One listener dying is that transport going deaf, not the node: the others keep
            // answering, and a reload can put working transport in its place.
            config.logger.error("listener for ${transport.id.value} stopped", e)
        }
    }

    /**
     * Answers an inbound connection and takes it through the public greeting. Returns null when
     * the source is throttled, or the peer never asked to authenticate - a probe, a scan, or a
     * link that simply died - so nothing is raised for it.
     */
    private suspend fun admit(
        owner: Transport,
        connection: Transport.InboundConnection,
    ): IncomingConnectionsManager.IncomingRequest? {
        val source = HandshakeSource(owner.id, connection.peer.endpoint)
        if (!throttle.reserve(source)) {
            config.logger.debug("refusing $source: pre-auth limit or rate limit reached")
            runCatching { connection.reject() }
            return null
        }

        val channel = connection.accept().getOrElse {
            throttle.release()
            config.logger.warn("could not answer ${connection.peer.advertisedName}", it)
            return null
        }

        val pump = FramePump(scope, channel)
        val inbound = try {
            negotiator.receive(pump, owner.capabilities, config.policy)
        } catch (e: Throwable) {
            throttle.release()
            pump.close()
            config.logger.debug("inbound from ${connection.peer.advertisedName} ended before auth: ${e.message}")
            return null
        }

        return IncomingRequest(
            source = source,
            connection = connection,
            confirmationCode = channel.confirmationCode,
            pump = pump,
            inbound = inbound,
        )
    }

    /**
     * A peer already past the greeting, holding an open link while the host decides.
     *
     * That link is a pre-authentication resource, so it is not held indefinitely: nobody settling
     * within the auth deadline is the same as a refusal.
     */
    private inner class IncomingRequest(
        private val source: HandshakeSource,
        private val connection: Transport.InboundConnection,
        override val confirmationCode: String?,
        private val pump: FramePump,
        private val inbound: HandshakeNegotiator.Inbound,
    ) : IncomingConnectionsManager.IncomingRequest {
        override val transport: SpiId = connection.transport
        override val peer: DiscoveredEndpoint = connection.peer

        private val settled = AtomicBoolean(false)

        /** Kills the request if the user never answers. */
        private val watchdog = scope.launch {
            delay(config.policy.authConfig.authTimeout)
            if (settled.compareAndSet(false, true)) {
                throttle.release()
                config.logger.debug("nobody answered the request from ${peer.advertisedName}")
                runCatching { inbound.reject("no answer") }
            }
        }

        override suspend fun accept(): Result<Unit> = runCatchingCancellable {
            if (!settled.compareAndSet(false, true)) {
                throw NetworkException.Transport("request from ${peer.advertisedName} already settled")
            }
            watchdog.cancel()

            val link = try {
                pump.runOrAbort {
                    inbound.accept(
                        confirmationCode = confirmationCode,
                        policy = config.policy
                    )
                }
            } catch (e: Throwable) {
                if (e is NetworkException.AuthenticationRejected) {
                    throttle.onAuthenticationFailed(source)
                }
                pump.close()
                throw e
            } finally {
                throttle.release()
            }
            throttle.onAuthenticated(source)

            val session = connectionsHolder.register(
                route = PeerRef(
                    deviceId = link.negotiated.peer.deviceId,
                    transport = transport,
                    endpoint = peer.endpoint
                ),
                link = link,
                policy = config.policy,
                relink = null, // No relink: this side never dialed, so it has nothing to dial back.
            )

            config.logger.debug("accepted a session with ${session.route.deviceId}")
        }

        override suspend fun reject(reason: CloseReason) {
            if (!settled.compareAndSet(false, true)) return
            watchdog.cancel()
            throttle.release()
            try {
                inbound.reject(reason.toString())
            } catch (e: Exception) {
                throw NetworkException.Transport("could not reject ${peer.advertisedName}", e)
            }
        }
    }
}
