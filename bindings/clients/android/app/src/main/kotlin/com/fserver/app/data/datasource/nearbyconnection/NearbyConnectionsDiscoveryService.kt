package com.fserver.app.data.datasource.nearbyconnection

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.ConnectionsStatusCodes
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber

internal class NearbyConnectionsDiscoveryService(
    private val context: Context,
) {
    fun start(): Flow<NearbyConnectionsEvent> = callbackFlow {
        val connectionsClient = Nearby.getConnectionsClient(context)
        val localEndpointName = defaultEndpointName(context)

        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(NEARBY_STRATEGY)
            .build()

        val endpointDiscoveryCallback = DiscoveryCallback(
            endpointFound = { endpoint, info ->
                val payloadCallback = DataReceiverCallback(
                    payloadReceived = { endpointId, payload ->
                        val receivedBytes = payload.asBytes()
                        if (receivedBytes == null) {
                            Timber.e("Received payload is null")
                            return@DataReceiverCallback
                        }

                        trySend(
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

                val lifecycleCallback = LifecycleCallback(
                    connectionInitiated = { endpointId, connectionInfo ->
                        trySend(
                            NearbyConnectionsEvent.Found(
                                NearbyConnectionsPeer(
                                    endpointId = endpointId,
                                    endpointName = connectionInfo.endpointName,
                                    authenticationDigits = connectionInfo.authenticationDigits,
                                )
                            )
                        )

                        connectionsClient.acceptConnection(
                            endpointId,
                            payloadCallback,
                        )
                    },
                    connectionResult = { endpointId, result ->
                        when (result.status.statusCode) {
                            ConnectionsStatusCodes.STATUS_OK -> {
                                Timber.d("Connection successful with endpoint ID: $endpointId")
                            }

                            ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                                Timber.d("Connection rejected by endpoint ID: $endpointId")
                                trySend(
                                    NearbyConnectionsEvent.Disconnected(endpointId)
                                )
                            }

                            ConnectionsStatusCodes.STATUS_ERROR -> {
                                Timber.d("Connection error with endpoint ID: $endpointId")
                                trySend(
                                    NearbyConnectionsEvent.PeerError(
                                        endpointId,
                                        result.status.statusCode
                                    )
                                )
                            }
                        }
                    },
                    disconnection = { endpointId ->
                        Timber.d("Disconnected from endpoint ID: $endpointId")
                        trySend(NearbyConnectionsEvent.Disconnected(endpointId))
                    },
                )

                connectionsClient.requestConnection(
                    localEndpointName,
                    endpoint,
                    lifecycleCallback,
                )
            },
            endpointLost = {
                trySend(NearbyConnectionsEvent.Disconnected(it))
            },
        )

        connectionsClient
            .startDiscovery(
                NEARBY_SERVICE_ID,
                endpointDiscoveryCallback,
                discoveryOptions
            )
            .addOnSuccessListener {
                trySend(NearbyConnectionsEvent.Idle)
                Timber.d("Discovery started successfully!")
            }
            .addOnFailureListener { e ->
                Timber.e(e, "Discovery failed")
                trySend(NearbyConnectionsEvent.Error(e))
                close()
            }

        awaitClose {
            connectionsClient.stopDiscovery()
            connectionsClient.stopAllEndpoints()
        }
    }

    /**
     * Listens for nearby devices.
     */
    private class DiscoveryCallback(
        private val endpointFound: (String, DiscoveredEndpointInfo) -> Unit,
        private val endpointLost: (String) -> Unit,
    ) : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            // A nearby device broadcasting your SERVICE_ID has been found!
            Timber.d("Device discovered! ID: $endpointId, Name: ${info.endpointName}")
            endpointFound(endpointId, info)
        }

        override fun onEndpointLost(endpointId: String) {
            // A previously discovered device is no longer in range
            Timber.d("Lost connection capability to device ID: $endpointId")
            endpointLost(endpointId)
        }
    }
}
