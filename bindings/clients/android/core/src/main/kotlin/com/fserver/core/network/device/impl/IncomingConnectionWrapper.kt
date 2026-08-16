package com.fserver.core.network.device.impl

import com.fserver.core.network.device.IncomingConnection
import com.fserver.core.network.impl.asDetectionMethod
import com.fserver.core.network.info.DetectionMethod
import com.fserver.net.connection.IncomingConnectionsManager

/**
 * Wrapper rather than a data copy: answering the request is the whole point of handing it out, and
 * that verb only exists on the `:net` object.
 */
internal class IncomingConnectionWrapper(val net: IncomingConnectionsManager.IncomingRequest) :
    IncomingConnection {
    override val deviceName: String = net.peer.advertisedName
    override val transport: DetectionMethod? = net.transport.asDetectionMethod()
    override val isSecured: Boolean = transport != null && net.confirmationCode != null

    override suspend fun accept(): Result<Unit> = net.accept()

    override suspend fun reject() = net.reject()
}
