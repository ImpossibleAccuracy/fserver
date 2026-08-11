package com.fserver.net.transport.android.datasource.nearbyconnection

import android.content.Context
import com.fserver.net.security.LocalIdentity
import com.fserver.net.transport.android.spi.nearbyconnection.NearbyConnectionsSPI
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.ConnectionInfo
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
    fun start(
        identity: LocalIdentity,
        config: NearbyConnectionsSPI.Config,
    ): Flow<NCDiscoveryEvent> = callbackFlow {
        val connectionsClient = Nearby.getConnectionsClient(context)
        val localEndpointName = identity.displayName

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
                            NCDeviceEvent.Message(
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
                            NCDeviceEvent.Found(
                                connectionInfo.asPeer(endpointId)
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
                                trySend(NCDeviceEvent.Connected(endpointId))
                            }

                            ConnectionsStatusCodes.STATUS_CONNECTION_REJECTED -> {
                                Timber.d("Connection rejected by endpoint ID: $endpointId")
                                trySend(
                                    NCDeviceEvent.Disconnected(endpointId)
                                )
                            }

                            ConnectionsStatusCodes.STATUS_ERROR -> {
                                Timber.d("Connection error with endpoint ID: $endpointId")
                                trySend(
                                    NCDeviceEvent.PeerError(
                                        endpointId,
                                        result.status.statusCode
                                    )
                                )
                            }
                        }
                    },
                    disconnection = { endpointId ->
                        Timber.d("Disconnected from endpoint ID: $endpointId")
                        trySend(NCDeviceEvent.Disconnected(endpointId))
                    },
                )

                connectionsClient.requestConnection(
                    localEndpointName,
                    endpoint,
                    lifecycleCallback,
                )
            },
            endpointLost = {
                trySend(NCDeviceEvent.Disconnected(it))
            },
        )

        connectionsClient
            .startDiscovery(
                config.serviceId,
                endpointDiscoveryCallback,
                discoveryOptions
            )
            .addOnSuccessListener {
                trySend(NCDiscoveryEvent.Registered)
                Timber.d("Discovery started successfully!")
            }
            .addOnFailureListener { e ->
                Timber.e(e, "Discovery failed")
                trySend(NCDiscoveryEvent.Error(e))
                close()
            }

        awaitClose {
            connectionsClient.stopDiscovery()
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

private fun ConnectionInfo.asPeer(
    endpointId: String
): NearbyConnectionsPeer = NearbyConnectionsPeer(
    endpointId = endpointId,
    endpointName = this.endpointName,
    authenticationDigits = this.authenticationDigits,
)
