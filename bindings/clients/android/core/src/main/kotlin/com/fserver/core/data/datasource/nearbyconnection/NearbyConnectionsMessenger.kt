package com.fserver.core.data.datasource.nearbyconnection

import android.content.Context
import com.fserver.core.data.utils.runBackgroundJob
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.Payload
import kotlinx.coroutines.suspendCancellableCoroutine
import timber.log.Timber
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Writes to connections the discovery and advertising services established.
 */
internal class NearbyConnectionsMessenger(
    private val context: Context,
) {
    private val connectionsClient by lazy { Nearby.getConnectionsClient(context) }

    /**
     * Hands [bytes] to Nearby for delivery to [endpointId].
     */
    suspend fun send(endpointId: String, bytes: ByteArray): Result<Unit> = runBackgroundJob {
        suspendCancellableCoroutine { continuation ->
            connectionsClient
                .sendPayload(endpointId, Payload.fromBytes(bytes))
                .addOnSuccessListener { continuation.resume(Unit) }
                .addOnFailureListener { e ->
                    Timber.e(e, "Failed to send %d bytes to %s", bytes.size, endpointId)
                    continuation.resumeWithException(e)
                }
        }
    }

    /**
     * Drops an established connection to [endpointId].
     */
    fun disconnect(endpointId: String) {
        connectionsClient.disconnectFromEndpoint(endpointId)
    }
}
