package com.fserver.net.transport.android.datasource.nearbyconnection

import android.content.Context
import com.fserver.net.discovery.PeerAttributes
import com.fserver.net.security.identity.IdentityStore
import com.fserver.net.transport.android.spi.nearbyconnection.NearbyConnectionsSPI
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.Payload
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.onSubscription
import timber.log.Timber
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * The single seam onto Nearby Connections.
 *
 * Everything connection-shaped lives here rather than in the advertising or discovery service,
 * because Nearby's state is per process and not per scan: the payload callback belongs to whoever
 * called `acceptConnection`, and connections outlive the flow that was being collected when they
 * were made. One client, one lifecycle callback, one payload callback.
 */
internal class NearbyConnectionsRepository internal constructor(
    context: Context,
    private val config: NearbyConnectionsSPI.Config,
    private val identityStore: IdentityStore,
) {
    private val connectionsClient = Nearby.getConnectionsClient(context)

    private val deviceEvents = MutableSharedFlow<NCDeviceEvent>(
        extraBufferCapacity = EVENT_BUFFER,
    )

    /** Hot: endpoints and connections carry on regardless of who is collecting. */
    val events: SharedFlow<NCDeviceEvent> = deviceEvents.asSharedFlow()

    /**
     * Channels waiting for frames from each endpoint.
     */
    private val inboundFrames = ConcurrentHashMap<String, Channel<ByteArray>>()

    private val payloadCallback = DataReceiverCallback { endpointId, payload ->
        val bytes = payload.asBytes()
        if (bytes == null) {
            Timber.e("Payload %d from %s carried no bytes", payload.id, endpointId)
            return@DataReceiverCallback
        }

        val delivered = inboundFrames[endpointId]?.trySend(bytes)?.isSuccess == true
        if (!delivered) {
            Timber.e(
                "Dropped %d bytes from %s: nothing is reading that link",
                bytes.size,
                endpointId
            )
        }
    }

    private val lifecycleCallback = LifecycleCallback(
        connectionInitiated = { endpointId, info ->
            Timber.d(
                "Connection initiated with %s, incoming=%b",
                endpointId,
                info.isIncomingConnection,
            )

            // Report only, wait for user to accept
            emit(
                NCDeviceEvent.ConnectionInitiated(
                    peer = NearbyConnectionsPeer(
                        endpointId = endpointId,
                        endpointInfo = info.endpointInfo,
                        authenticationDigits = info.authenticationDigits,
                    ),
                    incoming = info.isIncomingConnection,
                )
            )
        },
        connectionResult = { endpointId, result ->
            when (val statusCode = result.status.statusCode) {
                ConnectionsStatusCodes.STATUS_OK -> {
                    Timber.d("Connected to %s", endpointId)
                    emit(NCDeviceEvent.Connected(endpointId))
                }

                ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                    Timber.d("Connection to %s rejected", endpointId)
                    closeInbound(endpointId)
                    emit(NCDeviceEvent.Disconnected(endpointId))
                }

                else -> {
                    Timber.w("Connection to %s failed: %s", endpointId, statusName(statusCode))
                    closeInbound(endpointId)
                    emit(NCDeviceEvent.ConnectionFailed(endpointId, statusCode))
                }
            }
        },
        disconnection = { endpointId ->
            Timber.d("Disconnected from %s", endpointId)
            // Ends the frame stream, which is how the session above learns it lost its link.
            closeInbound(endpointId)
            emit(NCDeviceEvent.Disconnected(endpointId))
        },
    )

    private val advertisingService = NearbyConnectionsAdvertisingService(
        connectionsClient = connectionsClient,
        lifecycleCallback = lifecycleCallback
    )
    private val discoveryService = NearbyConnectionsDiscoveryService(
        connectionsClient = connectionsClient,
    )

    /**
     * What this device calls itself when it dials out. Advertising replaces it with the full
     * advertisement, so a peer we dial learns as much about us as one that found us.
     * Null until the first [startAdvertising]; dialling before then falls back to the minimum.
     */
    @Volatile
    private var localEndpointInfo: ByteArray? = null

    suspend fun startAdvertising(
        essential: Map<String, String>,
        optional: Map<String, String>,
    ): Flow<NCAdvertiserEvent> {
        val endpointInfo = NearbyEndpointInfo.encode(
            essential = defaultAttributes() + essential,
            optional = optional,
        )
        localEndpointInfo = endpointInfo

        return advertisingService.start(endpointInfo, config.serviceId)
    }

    fun startDiscovery(): Flow<NCDiscoveryEvent> = discoveryService
        .start(config.serviceId)
        .onEach {
            if (it is NCDeviceEvent) emit(it)
        }

    /**
     * Dials [endpointId] and returns once the link is up. Suspends through both of Nearby's
     * stages - the request, then the acceptance - so a caller that gets a link back can send on it.
     */
    suspend fun connect(endpointId: String): Result<NearbyConnectionsLink> = ncRunCatching {
        // Nearby refuses request for an endpoint this process is already talking to.
        // That failure means the link belongs to someone else,
        // so only a request this call placed itself may be torn down -
        // cleaning up unconditionally would kill a healthy link.
        var requested = false

        try {
            val peer = awaitEndpoint(
                endpointId = endpointId,
                track = {
                    val info = localEndpointInfo
                        ?: NearbyEndpointInfo.encode(defaultAttributes())

                    connectionsClient
                        .requestConnection(info, endpointId, lifecycleCallback)
                        .awaitCompletion()
                    requested = true
                },
                onEvent = { event ->
                    (event as? NCDeviceEvent.ConnectionInitiated)
                        // Not `incoming`: a peer dialing us at the same instant
                        // is a different connection, and answering it here would settle the wrong one.
                        ?.takeIf { it.peer.endpointId == endpointId && !it.incoming }
                        ?.peer
                },
            )

            NearbyConnectionsLink(peer, acceptConnection(endpointId))
        } catch (t: Throwable) {
            if (requested) disconnect(endpointId)
            throw t
        }
    }

    /**
     * Accepts the pending connection from [NearbyConnectionsPeer.endpointId] and returns once it is up. Call only after
     * the user has confirmed [NearbyConnectionsPeer.authenticationDigits] match the other device.
     */
    suspend fun accept(peer: NearbyConnectionsPeer): Result<NearbyConnectionsLink> = ncRunCatching {
        withCleanupOnFailure(peer.endpointId) {
            NearbyConnectionsLink(peer, acceptConnection(peer.endpointId))
        }
    }

    /** Declines the pending connection from [endpointId]. */
    suspend fun reject(endpointId: String): Result<Unit> = ncRunCatching {
        connectionsClient.rejectConnection(endpointId).awaitCompletion()
    }

    suspend fun send(endpointId: String, bytes: ByteArray): Result<Unit> = try {
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes)).awaitCompletion()
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Throwable) {
        Timber.e(e, "Failed to send %d bytes to %s", bytes.size, endpointId)
        Result.failure(e)
    }

    fun disconnect(endpointId: String) {
        closeInbound(endpointId)
        connectionsClient.disconnectFromEndpoint(endpointId)
    }

    fun shutdown() {
        connectionsClient.stopDiscovery()
        connectionsClient.stopAdvertising()
        connectionsClient.stopAllEndpoints()

        inboundFrames.keys.toList().forEach(::closeInbound)
    }

    // ------------------------------------------------------------------ internals

    /** Buffers frames from the moment Nearby may start delivering them, and waits for the link. */
    private suspend fun acceptConnection(endpointId: String): Flow<ByteArray> {
        val frames = Channel<ByteArray>(Channel.UNLIMITED)
        inboundFrames.put(endpointId, frames)?.close()

        awaitEndpoint(
            endpointId = endpointId,
            track = {
                connectionsClient
                    .acceptConnection(endpointId, payloadCallback)
                    .awaitCompletion()
            },
            onEvent = { event ->
                (event as? NCDeviceEvent.Connected)?.takeIf { it.endpointId == endpointId }
            },
        )

        return frames.consumeAsFlow()
    }

    /**
     * Await the first event that [onEvent] returns non-null for, throwing if the endpoint is lost or the connection fails.
     *
     * @param endpointId Target endpoint to watch for events from.
     * @param track A suspend function that starts the connection process.
     * Called when collector is ready to accept events, so it doesn't miss any.
     * @param onEvent A function that returns a non-null value when the desired event is received.
     */
    private suspend fun <T : Any> awaitEndpoint(
        endpointId: String,
        track: suspend () -> Unit,
        onEvent: (NCDeviceEvent) -> T?,
    ): T = events
        .onSubscription {
            track()
        }
        .mapNotNull { event ->
            when (event) {
                is NCDeviceEvent.ConnectionFailed if event.endpointId == endpointId ->
                    throw IOException(
                        "connection to $endpointId failed: ${statusName(event.statusCode)}"
                    )

                is NCDeviceEvent.Disconnected if event.endpointId == endpointId ->
                    throw IOException("connection to $endpointId was refused or dropped")

                is NCDiscoveryEvent.EndpointLost if event.endpointId == endpointId ->
                    throw IOException("endpoint $endpointId went out of range")

                else -> onEvent(event)
            }
        }
        .first()

    /**
     * A half-open connection is invisible to the caller but still occupies Nearby's radios.
     */
    private suspend fun <T> withCleanupOnFailure(endpointId: String, block: suspend () -> T): T =
        try {
            block()
        } catch (t: Throwable) {
            disconnect(endpointId)
            throw t
        }

    private fun closeInbound(endpointId: String) {
        inboundFrames.remove(endpointId)?.close()
    }

    private fun emit(event: NCDeviceEvent) {
        if (!deviceEvents.tryEmit(event)) {
            Timber.e("Dropped %s: a collector is more than %d events behind", event, EVENT_BUFFER)
        }
    }

    /** The least a peer needs to tell this device from another, before `:net` adds the rest. */
    private suspend fun defaultAttributes(): Map<String, String> {
        val local = identityStore.local()
        return mapOf(
            PeerAttributes.DEVICE_ID to local.deviceId,
            PeerAttributes.DISPLAY_NAME to local.displayName,
        )
    }

    private companion object {
        const val EVENT_BUFFER = 256
    }
}

/** A live link: who is on the other end, and the frames arriving from them. */
internal class NearbyConnectionsLink(
    val peer: NearbyConnectionsPeer,
    /** Completes when the link goes down. Collectable once. */
    val inbound: Flow<ByteArray>,
)

private fun statusName(statusCode: Int): String =
    "${ConnectionsStatusCodes.getStatusCodeString(statusCode)} ($statusCode)"

/** [runCatching] that still lets structured cancellation through. */
private inline fun <T> ncRunCatching(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Result.failure(e)
}
