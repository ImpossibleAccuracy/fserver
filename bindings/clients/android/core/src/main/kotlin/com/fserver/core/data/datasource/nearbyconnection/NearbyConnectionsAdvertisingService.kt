package com.fserver.core.data.datasource.nearbyconnection

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.launch
import timber.log.Timber

/**
 * Makes this device findable by other peers over Nearby's own radios (BLE / Bluetooth /
 * Wi-Fi Direct), and surfaces the connection requests that arrive.
 *
 * Incoming connections are **not** accepted here. [start] emits
 * [NearbyConnectionsEvent.Found] carrying [NearbyConnectionsPeer.authenticationDigits] and then
 * waits: the caller must confirm with the user and call [accept] or [reject]. Nearby's only
 * man-in-the-middle protection is that digit comparison, so auto-accepting would hand any device
 * in radio range an encrypted channel into the app with no user involvement.
 *
 * Single instance per process - [accept] registers the payload callback that feeds [start]'s
 * flow, so calling it on a different instance than the one advertising would silently route
 * payloads nowhere.
 */
class NearbyConnectionsAdvertisingService(
    private val context: Context,
) {
    private val connectionsClient by lazy { Nearby.getConnectionsClient(context) }

    /**
     * Events raised outside [start]'s callback flow - payloads arrive through a callback owned by
     * this class, because [accept] can only register one after the flow is already running.
     */
    private val outOfBandEvents = MutableSharedFlow<NearbyConnectionsEvent>(
        extraBufferCapacity = EVENT_BUFFER,
    )

    private val payloadCallback = DataReceiverCallback(
        payloadReceived = { endpointId, payload ->
            val receivedBytes = payload.asBytes()
            if (receivedBytes == null) {
                Timber.e("Received payload is null")
                return@DataReceiverCallback
            }

            outOfBandEvents.tryEmit(
                NearbyConnectionsEvent.Message(
                    NearbyConnectionsMessage(
                        endpointId = endpointId,
                        messageId = payload.id,
                        message = receivedBytes,
                    )
                )
            )
        },
        payloadTransferUpdate = { _, _ ->
            // Track progress of incoming/outgoing file or byte transfers
        },
    )

    fun start(): Flow<NearbyConnectionsEvent> = callbackFlow {
        val localEndpointName = defaultEndpointName(context)

        // Payloads and post-accept failures are produced outside this flow; the collector dies
        // with it.
        launch {
            outOfBandEvents.collect { trySend(it) }
        }

        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(NEARBY_STRATEGY)
            .build()

        val lifecycleCallback = LifecycleCallback(
            connectionInitiated = { endpointId, connectionInfo ->
                Timber.d("Connection requested by endpoint ID: $endpointId")

                // Report only. Accepting is the caller's decision, after the user has compared
                // the digits.
                trySend(
                    NearbyConnectionsEvent.Found(
                        NearbyConnectionsPeer(
                            endpointId = endpointId,
                            endpointName = connectionInfo.endpointName,
                            authenticationDigits = connectionInfo.authenticationDigits,
                        )
                    )
                )
            },
            connectionResult = { endpointId, result ->
                when (val statusCode = result.status.statusCode) {
                    ConnectionsStatusCodes.STATUS_OK -> {
                        Timber.d("Connection successful with endpoint ID: $endpointId")
                        trySend(NearbyConnectionsEvent.Connected(endpointId))
                    }

                    ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                        Timber.d("Connection rejected for endpoint ID: $endpointId")
                        trySend(NearbyConnectionsEvent.Disconnected(endpointId))
                    }

                    else -> {
                        Timber.d("Connection failed for $endpointId, status=$statusCode")
                        trySend(NearbyConnectionsEvent.PeerError(endpointId, statusCode))
                    }
                }
            },
            disconnection = { endpointId ->
                Timber.d("Disconnected from endpoint ID: $endpointId")
                trySend(NearbyConnectionsEvent.Disconnected(endpointId))
            },
        )

        connectionsClient
            .startAdvertising(
                localEndpointName,
                NEARBY_SERVICE_ID,
                lifecycleCallback,
                advertisingOptions,
            )
            .addOnSuccessListener {
                trySend(NearbyConnectionsEvent.Idle)
                Timber.d("Advertising started as %s", localEndpointName)
            }
            .addOnFailureListener { e ->
                Timber.e(e, "Advertising failed")
                trySend(NearbyConnectionsEvent.Error(e))
                close()
            }

        awaitClose {
            // Only stops being findable. Connections already accepted stay up - they are the
            // point of having advertised, and tearing them down here would also kill the
            // discovery side, which shares this process's ConnectionsClient.
            connectionsClient.stopAdvertising()
        }
    }

    /**
     * Accepts the pending connection from [endpointId]. Call only after the user has confirmed
     * [NearbyConnectionsPeer.authenticationDigits] match what the other device shows.
     */
    fun accept(endpointId: String) {
        connectionsClient
            .acceptConnection(endpointId, payloadCallback)
            .addOnFailureListener { e ->
                Timber.e(e, "Failed to accept connection from %s", endpointId)
                outOfBandEvents.tryEmit(NearbyConnectionsEvent.Error(e))
            }
    }

    /**
     * Declines the pending connection from [endpointId]. The resulting
     * [NearbyConnectionsEvent.Disconnected] arrives through the connection result callback.
     */
    fun reject(endpointId: String) {
        connectionsClient
            .rejectConnection(endpointId)
            .addOnFailureListener { e ->
                Timber.e(e, "Failed to reject connection from %s", endpointId)
                outOfBandEvents.tryEmit(NearbyConnectionsEvent.Error(e))
            }
    }

    /**
     * Drops an established connection to [endpointId].
     */
    fun disconnect(endpointId: String) {
        connectionsClient.disconnectFromEndpoint(endpointId)
    }

    private companion object {
        const val EVENT_BUFFER = 64
    }
}
