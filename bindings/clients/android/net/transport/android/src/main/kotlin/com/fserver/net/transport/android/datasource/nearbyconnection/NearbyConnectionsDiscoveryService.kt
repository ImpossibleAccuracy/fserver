package com.fserver.net.transport.android.datasource.nearbyconnection

import com.google.android.gms.nearby.connection.ConnectionsClient
import com.google.android.gms.nearby.connection.DiscoveredEndpointInfo
import com.google.android.gms.nearby.connection.DiscoveryOptions
import com.google.android.gms.nearby.connection.EndpointDiscoveryCallback
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber

/**
 * Browses for endpoints advertising the same service id.
 *
 * Finding is all it does: nothing is dialled here. Connecting is
 * [NearbyConnectionsRepository.connect], driven by the host once the user has picked a peer -
 * an automatic `requestConnection` per endpoint would have this device connecting to everything
 * in radio range.
 */
internal class NearbyConnectionsDiscoveryService(
    private val connectionsClient: ConnectionsClient,
) {
    fun start(serviceId: String): Flow<NCDiscoveryEvent> = callbackFlow {
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(NEARBY_STRATEGY)
            .build()

        val discoveryCallback = DiscoveryCallback(
            endpointFound = { endpointId, info ->
                Timber.d("Endpoint found: %s (%s)", endpointId, info.endpointName)
                trySend(NCDiscoveryEvent.EndpointFound(endpointId, info.endpointInfo))
            },
            endpointLost = { endpointId ->
                Timber.d("Endpoint lost: %s", endpointId)
                trySend(NCDiscoveryEvent.EndpointLost(endpointId))
            },
        )

        connectionsClient
            .startDiscovery(serviceId, discoveryCallback, discoveryOptions)
            .addOnSuccessListener {
                Timber.d("Discovery started")
                trySend(NCDiscoveryEvent.Registered)
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

    /** Endpoints coming in and out of radio range. Says nothing about connections. */
    internal class DiscoveryCallback(
        private val endpointFound: (String, DiscoveredEndpointInfo) -> Unit,
        private val endpointLost: (String) -> Unit,
    ) : EndpointDiscoveryCallback() {
        override fun onEndpointFound(endpointId: String, info: DiscoveredEndpointInfo) {
            endpointFound(endpointId, info)
        }

        override fun onEndpointLost(endpointId: String) {
            endpointLost(endpointId)
        }
    }
}
