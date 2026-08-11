package com.fserver.net.transport.android.datasource.nearbyconnection

import com.google.android.gms.nearby.connection.AdvertisingOptions
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionsClient
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import timber.log.Timber

/**
 * Makes this device findable over Nearby's own radios (BLE / Bluetooth / Wi-Fi Direct).
 *
 * Only the advertising registration lives here. Connections that arrive while advertising are
 * reported - and answered - by [NearbyConnectionsRepository], because Nearby keeps them alive
 * past the end of this flow.
 */
internal class NearbyConnectionsAdvertisingService(
    private val connectionsClient: ConnectionsClient,
    private val lifecycleCallback: ConnectionLifecycleCallback,
) {
    fun start(
        endpointInfo: ByteArray,
        serviceId: String,
    ): Flow<NCAdvertiserEvent> = callbackFlow {
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(NEARBY_STRATEGY)
            .build()

        connectionsClient
            .startAdvertising(
                endpointInfo,
                serviceId,
                lifecycleCallback,
                advertisingOptions,
            )
            .addOnSuccessListener {
                Timber.d("Advertising started, %d bytes of endpoint info", endpointInfo.size)
                trySend(NCAdvertiserEvent.Registered)
            }
            .addOnFailureListener { e ->
                Timber.e(e, "Advertising failed")
                trySend(NCAdvertiserEvent.Error(e))
                close()
            }

        awaitClose {
            // Only stops being findable. Connections already accepted stay up - they are the
            // point of having advertised, and tearing them down here would also kill the
            // discovery side, which shares this process's ConnectionsClient.
            connectionsClient.stopAdvertising()
        }
    }
}
