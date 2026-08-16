package com.fserver.app.presentation.model

import com.fserver.core.domain.model.connection.IncomingConnection
import com.fserver.core.domain.model.network.DetectionMethod


/**
 * A device asking to connect, as the sheet needs it.
 *
 * The request arrives before any handshake, so nothing about the peer is proven: all there is to
 * show is the advertised name and, when the transport produced one, its confirmation digits.
 */
data class IncomingConnectionUi(
    val deviceName: String,
    val via: DetectionMethod?,
    val isUnsecured: Boolean,
)

fun IncomingConnection.toUi(): IncomingConnectionUi = IncomingConnectionUi(
    deviceName = deviceName,
    via = transport,
    // Neither a fingerprint nor digits: nothing here proves which device this is.
    isUnsecured = !isSecured,
)
