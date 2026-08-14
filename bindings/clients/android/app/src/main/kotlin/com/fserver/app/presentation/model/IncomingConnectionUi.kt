package com.fserver.app.presentation.model

import com.fserver.net.connection.IncomingConnectionsManager


/**
 * A device asking to connect, as the sheet needs it.
 *
 * The request arrives before any handshake, so nothing about the peer is proven: all there is to
 * show is the advertised name and, when the transport produced one, its confirmation digits.
 */
data class IncomingConnectionUi(
    val deviceName: String,
    val via: String,
    val isUnsecured: Boolean,
)

fun IncomingConnectionsManager.IncomingRequest.toUi(): IncomingConnectionUi = IncomingConnectionUi(
    deviceName = peer.advertisedName,
    via = transport.value,
    // Neither a fingerprint nor digits: nothing here proves which device this is.
    isUnsecured = confirmationCode != null,
)
