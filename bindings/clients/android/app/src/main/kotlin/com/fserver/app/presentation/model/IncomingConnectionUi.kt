package com.fserver.app.presentation.model

import com.fserver.net.connection.ConnectionManager

/**
 * A device asking to connect, as the sheet needs it.
 *
 * The request arrives before any handshake, so nothing about the peer is proven: all there is to
 * show is the advertised name and, when the transport produced one, its confirmation digits.
 */
data class IncomingConnectionUi(
    val deviceName: String,
    val via: String,
    /** Pre-grouped for `DkFingerprintBlock`. Null when nothing about the peer is proven yet. */
    val fingerprintGroups: List<String>?,
    /** Digits shown on both screens, when the transport produced a pair. */
    val confirmationCode: String?,
)

fun ConnectionManager.IncomingRequest.toUi(): IncomingConnectionUi = IncomingConnectionUi(
    deviceName = peer.advertisedName,
    via = transport.value,
    fingerprintGroups = null,
    confirmationCode = confirmationCode,
)
