package com.fserver.net.transport.android.datasource.nearbyconnection

import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy
import com.google.android.gms.tasks.Task
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Radio topology both sides must agree on: an advertiser using one strategy is invisible to a
 * discoverer using another.
 */
internal val NEARBY_STRATEGY: Strategy = Strategy.P2P_CLUSTER

/**
 * Awaits a Nearby call. Only says the call was accepted - what it set in motion is reported
 * through the lifecycle callback.
 */
internal suspend fun Task<*>.awaitCompletion(): Unit = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { continuation.resume(Unit) }
    addOnFailureListener(continuation::resumeWithException)
    addOnCanceledListener { continuation.cancel() }
}

/** Receives payloads over an accepted connection. */
internal class DataReceiverCallback(
    private val payloadReceived: (String, Payload) -> Unit,
) : PayloadCallback() {
    override fun onPayloadReceived(endpointId: String, payload: Payload) {
        if (payload.type == Payload.Type.BYTES) {
            payloadReceived(endpointId, payload)
        }
    }

    override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
        // Bytes payloads arrive whole through onPayloadReceived; nothing to track until this
        // transport starts using stream or file payloads.
    }
}

/** Lifecycle of connections this process dialed and of the ones it was offered. */
internal class LifecycleCallback(
    private val connectionInitiated: (String, ConnectionInfo) -> Unit,
    private val connectionResult: (String, ConnectionResolution) -> Unit,
    private val disconnection: (String) -> Unit,
) : ConnectionLifecycleCallback() {
    override fun onConnectionInitiated(endpointId: String, connectionInfo: ConnectionInfo) {
        connectionInitiated(endpointId, connectionInfo)
    }

    override fun onConnectionResult(endpointId: String, result: ConnectionResolution) {
        connectionResult(endpointId, result)
    }

    override fun onDisconnected(endpointId: String) {
        disconnection(endpointId)
    }
}
