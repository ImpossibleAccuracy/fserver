package com.fserver.core.network.device.impl.mapper

import com.fserver.core.network.auth.AuthMethod
import com.fserver.core.network.auth.Greeting
import com.fserver.core.network.device.model.ForeignDevice
import com.fserver.core.network.impl.asTransportKind
import com.fserver.net.connection.PeerRef
import com.fserver.net.connection.ProbeResult
import com.fserver.net.security.NegotiatedParameters
import com.fserver.net.security.identity.PeerIdentity

/** `:net` shapes as `:core` reports them. Shared by the repository and the feeds it hands out. */

internal fun ProbeResult.toDomain() = Greeting(
    peer = route.endpoint.asPeerLocator(),
    protocolVersions = greeting.protocolVersions,
    methods = greeting.methods.mapNotNull { AuthMethod.fromId(it) },
)

internal fun PeerRef.toDomain() = ForeignDevice.DeviceRoute(
    address = endpoint.address,
    foundBy = transport.asTransportKind(),
)

internal fun PeerIdentity.toDomain(negotiated: NegotiatedParameters) = ForeignDevice.Handshake(
    fingerprint = fingerprint.value,
    protocolVersion = negotiated.protocolVersion,
    cipherSuite = negotiated.cipherSuite.name,
)
