package com.fserver.core.data.datasource.nearbyconnection

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.google.android.gms.nearby.connection.ConnectionInfo
import com.google.android.gms.nearby.connection.ConnectionLifecycleCallback
import com.google.android.gms.nearby.connection.ConnectionResolution
import com.google.android.gms.nearby.connection.Payload
import com.google.android.gms.nearby.connection.PayloadCallback
import com.google.android.gms.nearby.connection.PayloadTransferUpdate
import com.google.android.gms.nearby.connection.Strategy

/**
 * Radio topology both sides must agree on: an advertiser using one strategy is invisible to a
 * discoverer using another.
 */
internal val NEARBY_STRATEGY = Strategy.P2P_CLUSTER

/**
 * Advertiser and discoverer only see each other when this matches exactly, so it is part of the
 * protocol, not a build detail.
 *
 * Literal rather than `BuildConfig.APPLICATION_ID`: `:core` ships to other UIs with their own
 * application ids, and a dev flavour must still be able to see a prod peer.
 */
internal const val NEARBY_SERVICE_ID = "com.fserver.app"

/**
 * Name this device presents to peers. User-set device name when the platform has one, hardware
 * model otherwise.
 */
@Deprecated("migrate to shared prefs")
internal fun defaultEndpointName(context: Context): String =
    Settings.Global
        // Settings.Global.DEVICE_NAME is API 25; minSdk is 24.
        .getString(context.contentResolver, "device_name")
        ?.takeIf { it.isNotBlank() }
        ?: Build.MODEL

/**
 * Receives payloads over an accepted connection.
 */
internal class DataReceiverCallback(
    private val payloadReceived: (String, Payload) -> Unit,
    private val payloadTransferUpdate: (String, PayloadTransferUpdate) -> Unit,
) : PayloadCallback() {
    override fun onPayloadReceived(endpointId: String, payload: Payload) {
        if (payload.type == Payload.Type.BYTES) {
            payloadReceived(endpointId, payload)
        }
    }

    override fun onPayloadTransferUpdate(endpointId: String, update: PayloadTransferUpdate) {
        // Track progress of incoming/outgoing file or byte transfers
        payloadTransferUpdate(endpointId, update)
    }
}

/**
 * Listens for single connection lifecycle.
 */
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
