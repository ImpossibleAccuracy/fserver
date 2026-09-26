package com.fserver.core.network.device.impl

import com.fserver.common.exception.MalformedQrException
import com.fserver.common.exception.NetworkException
import com.fserver.common.utils.chainWith
import com.fserver.core.Constants
import com.fserver.core.di.BackgroundScope
import com.fserver.core.lifecycle.network.AutoAcceptCoordinator
import com.fserver.core.network.DeviceUnreachableException
import com.fserver.core.network.NetworkController
import com.fserver.core.network.PeerIdentityMismatchException
import com.fserver.core.network.RequirementsNotMetException
import com.fserver.core.network.auth.AuthCredentials
import com.fserver.core.network.auth.Greeting
import com.fserver.core.network.auth.impl.InteractivePeerAuthenticator
import com.fserver.core.network.device.DeviceAdvertising
import com.fserver.core.network.device.DeviceDiscovery
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.device.IncomingConnection
import com.fserver.core.network.device.OnlineDevices
import com.fserver.core.network.device.impl.mapper.toDomain
import com.fserver.core.network.device.impl.mapper.toKnownRoute
import com.fserver.core.network.device.json.JsonQrCodeParser
import com.fserver.core.network.device.json.JsonQrCodeWriter
import com.fserver.core.network.device.model.PendingConfirmation
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.impl.asTransportKind
import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.network.info.currentNetworkId
import com.fserver.core.network.info.model.PeerLocator
import com.fserver.core.requirement.RequirementsChecker
import com.fserver.core.store.FServerStorage
import com.fserver.net.connection.PeerRef
import com.fserver.net.connection.ProbeResult
import com.fserver.net.security.auth.AuthRequest
import com.fserver.net.security.auth.pake.OneTimeCodeAuthMethod
import com.fserver.net.security.auth.pake.PakeAuthMethod
import com.fserver.net.security.auth.pake.PinAuthMethod
import com.fserver.net.session.CloseReason
import com.fserver.net.session.PeerSession
import com.fserver.net.spi.SpiId
import com.fserver.net.spi.TransportEndpoint
import com.fserver.net.transport.android.spi.ip.DirectIpEndpoint
import com.fserver.net.transport.android.spi.nearbyconnection.NearbyConnectionsTransportEndpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.shareIn
import timber.log.Timber

internal class DevicesRepositoryImpl(
    private val network: NetworkController,
    private val requirementsChecker: RequirementsChecker,
    private val networkInfoRepository: NetworkInfoRepository,
    private val jsonQrCodeParser: JsonQrCodeParser,
    private val jsonQrCodeWriter: JsonQrCodeWriter,
    private val interactiveAuthenticator: InteractivePeerAuthenticator,
    private val storage: FServerStorage,
    private val reachability: ReachabilityTracker,
    private val autoAccept: AutoAcceptCoordinator,
    private val backgroundScope: BackgroundScope,
) : DevicesRepository {

    override val discovery: DeviceDiscovery by lazy {
        DeviceDiscoveryImpl(network = network, requirementsChecker = requirementsChecker)
    }

    override val advertising: DeviceAdvertising by lazy {
        DeviceAdvertisingImpl(
            network = network,
            requirementsChecker = requirementsChecker,
            networkInfoRepository = networkInfoRepository,
            qrCodeWriter = jsonQrCodeWriter,
            storage = storage,
        )
    }

    override val devices: OnlineDevices by lazy {
        OnlineDevicesImpl(network = network, storage = storage)
    }

    /**
     * What the host has to ask its user about. A device the engine answers by itself never
     * appears here - see [AutoAcceptCoordinator] for which ones those are.
     */
    override val incoming: Flow<IncomingConnection> = network.incomingConnections.incoming
        .filterNot { autoAccept.handles(it) }
        .map { IncomingConnectionWrapper(it) }
        .shareIn(
            scope = backgroundScope,
            started = SharingStarted.Eagerly,
        )

    override val pendingConfirmation: Flow<PendingConfirmation?>
        get() = interactiveAuthenticator.pending

    override fun resolvePendingConfirmation(accept: Boolean) =
        interactiveAuthenticator.resolve(accept)

    override suspend fun disconnect(deviceId: String): Result<Unit> = runCatching {
        network.incomingConnections.session(deviceId)?.close(CloseReason.Normal)
    }

    /**
     * A greeting is advisory and the device id behind it is whatever the advertisement claimed, so
     * nothing here is written down: a route recorded from a probe would let anyone announcing a
     * trusted device's id replace that device's stored address. Routes are recorded in [connect],
     * against the identity the handshake proved.
     */
    override suspend fun probe(arguments: PeerLocator): Result<Greeting> =
        dial(
            arguments = arguments,
            byDeviceId = ::probeDiscovered,
            byRoute = ::probeRoute,
        ).map { it.toDomain() }

    override suspend fun connect(
        arguments: PeerLocator,
        credentials: AuthCredentials?,
    ): Result<Unit> {
        val request = AuthRequest(
            method = credentials?.method?.authMethodId,
            params = when (credentials) {
                is AuthCredentials.Password -> PakeAuthMethod.PakeAuthParams(credentials.password)
                is AuthCredentials.Pin -> PinAuthMethod.PinAuthParams(credentials.pin)
                is AuthCredentials.OneTimeCode ->
                    OneTimeCodeAuthMethod.OneTimeCodeParams(credentials.code)
                else -> null
            }
        )

        // What the caller asked for, when it named a device at all. A bare address does not: which
        // device is behind it is not known until the handshake says so.
        val expected = when (arguments) {
            is PeerLocator.DiscoveredDevice -> arguments.id
            is PeerLocator.KnownDevice -> arguments.id
            else -> null
        }

        return dial(
            arguments = arguments,
            byDeviceId = { deviceId ->
                connectKnown(deviceId = deviceId, request = request)
                    .verifiedAs(expected ?: deviceId)
            },
            byRoute = { peer ->
                connectRoute(peer = peer, request = request).verifiedAs(expected)
            },
        )
            .onSuccess { session ->
                reachability.recordSuccess(session.identity.deviceId)

                rememberRoute(
                    deviceId = session.identity.deviceId,
                    endpoint = session.route.endpoint
                )
            }
            .onFailure { failure ->
                if (expected != null) reachability.recordFailure(expected, failure)
            }
            .map { }
    }

    /**
     * Turns a [PeerLocator] into an actual attempt:
     * - everything that is already a route goes to [byRoute],
     * - anything that only names a device goes to [byDeviceId].
     *
     * Routing only. Whether the OS will let an attempt through is asked by the ends that make it -
     * [probeDiscovered] / [probeRoute], [connectKnown] / [connectRoute] - because that is where the
     * route, and so the transport, is finally known.
     */
    private suspend fun <T> dial(
        arguments: PeerLocator,
        byDeviceId: suspend (String) -> Result<T>,
        byRoute: suspend (PeerRef) -> Result<T>,
    ): Result<T> = when (arguments) {
        is PeerLocator.DiscoveredDevice -> byDeviceId(arguments.id)

        is PeerLocator.Ip -> byRoute(arguments.toPeerRef())

        // A code naming no address still reaches a device that advertises: over Nearby there is
        // nothing to dial, so the device id is looked up among the ones discovery can see.
        is PeerLocator.QrPayload -> dialQr(
            arguments = arguments,
            byDeviceId = byDeviceId,
            byRoute = byRoute,
        )

        is PeerLocator.NearbyEndpoint -> findNearbyEndpoint(arguments)
            ?.let { byRoute(it) }
            ?: Result.failure(IllegalArgumentException("Endpoint ${arguments.endpointId} not found"))

        is PeerLocator.KnownDevice -> dialKnown(arguments.id, byDeviceId, byRoute)
    }

    private suspend fun <T> dialQr(
        arguments: PeerLocator.QrPayload,
        byDeviceId: suspend (String) -> Result<T>,
        byRoute: suspend (PeerRef) -> Result<T>,
    ): Result<T> {
        val code = jsonQrCodeParser.parse(arguments.payload)
            ?: return Result.failure(MalformedQrException())

        val results = buildList<suspend () -> Result<T>> {
            if (code.deviceId != null) {
                val found = network.incomingConnections.session(code.deviceId) != null ||
                        network.peerDiscovery.peers.value.any { it.advertised.deviceId == code.deviceId }

                if (found) add { byDeviceId(code.deviceId) }
            }

            if (code.directIp != null) {
                val peer = PeerRef.build(
                    DirectIpEndpoint(
                        host = code.directIp.host,
                        port = code.directIp.port ?: Constants.DEFAULT_PORT
                    )
                )

                add { byRoute(peer) }
            }

            if (code.nearby != null) {
                val peer = PeerRef.build(
                    NearbyConnectionsTransportEndpoint(code.nearby.endpointId)
                )

                add { byRoute(peer) }
            }
        }

        if (results.isEmpty()) {
            throw MalformedQrException("QR contains no data")
        }

        val fails = mutableListOf<Result<T>>()

        for (function in results) {
            val result = function()
            if (result.isSuccess) return result
            else fails += result
        }

        return fails.first()
    }

    /**
     * A device out of the trust records: discovery first - it answers with the session that is
     * already open, and with the route the device is on now rather than the one it was on last
     * time - then the route written down last time.
     *
     * Falling through to the next candidate is for transport that could not carry the attempt.
     * A peer that answered and refused stays refused: asking again over another route would only
     * make it refuse twice, and prompt its user twice.
     */
    private suspend fun <T> dialKnown(
        deviceId: String,
        byDeviceId: suspend (String) -> Result<T>,
        byRoute: suspend (PeerRef) -> Result<T>,
    ): Result<T> {
        val known = storage.trust.findKnownRoute(deviceId)

        // A stored route that names a device again would loop straight back into here.
        val stored = known?.asPeerLocator()?.takeUnless { it is PeerLocator.KnownDevice }

        // Try discovery first, because it is more likely to succeed and gives a more up-to-date route
        val discovered = dial(
            arguments = PeerLocator.DiscoveredDevice(deviceId),
            byDeviceId = byDeviceId,
            byRoute = byRoute,
        )

        // Nothing left to dial: say so in terms of the route, not of the failed discovery lookup.
        if (stored == null) {
            return discovered.recoverCatching {
                throw DeviceUnreachableException(deviceId, known?.transport, it)
            }
        }

        if (discovered.isSuccess || discovered.refusedByPeer()) return discovered

        return dial(
            arguments = stored,
            byDeviceId = byDeviceId,
            byRoute = byRoute,
        )
    }

    /**
     * Fails the attempt unless the peer that completed the handshake is the one that was dialled,
     * closing the session it opened. Everything that named [expectedDeviceId] before the handshake
     * - an advertisement, a stored route - was unauthenticated.
     */
    private suspend fun Result<PeerSession<FileServerMessages>>.verifiedAs(
        expectedDeviceId: String?,
    ): Result<PeerSession<FileServerMessages>> = mapCatching { session ->
        val actual = session.identity.deviceId

        if (expectedDeviceId != null && actual != expectedDeviceId) {
            session.close(CloseReason.Local("dialled $expectedDeviceId"))
            throw PeerIdentityMismatchException(expected = expectedDeviceId, actual = actual)
        }

        session
    }

    /** Greets a device discovery can still see, over the route it is advertising now. */
    private suspend fun probeDiscovered(deviceId: String): Result<ProbeResult> {
        val peer = network.peerDiscovery.peer(deviceId).firstOrNull()
            ?: return Result.failure(IllegalArgumentException("Device $deviceId not found"))

        return network.requestManager.probe(peer)
    }

    /** Greets whatever is on [peer] - a typed address, a scanned one, a route written down. */
    private suspend fun probeRoute(peer: PeerRef): Result<ProbeResult> {
        transportRefusal(peer.transport)?.let { return Result.failure(it) }

        return network.requestManager.probe(peer)
    }

    /** Reconnects to a device already known by [deviceId] - discovered, or previously probed. */
    private suspend fun connectKnown(
        deviceId: String,
        request: AuthRequest
    ): Result<PeerSession<FileServerMessages>> {
        // A session already up asks nothing of the OS, so this comes before the gate.
        network.incomingConnections.session(deviceId)?.let {
            return Result.success(it)
        }

        return network.peerDiscovery.peers.value
            .find { it.advertised.deviceId == deviceId }
            ?.let {
                network.requestManager.connect(
                    peer = it,
                    request = request,
                )
            } // Try to connect by discovered route first
            .chainWith {
                // Fallback to previously probed route, if any.
                network.requestManager.profile(deviceId)
                    ?.let {
                        network.requestManager.connect(
                            peer = it.route,
                            request = request,
                        )
                    }
            }
            ?: Result.failure(
                // Device not found anywhere, abort
                IllegalArgumentException("Device $deviceId not found")
            )
    }

    /** Dials whatever is on [peer] - a typed address, a scanned one, a route written down. */
    private suspend fun connectRoute(
        peer: PeerRef,
        request: AuthRequest,
    ): Result<PeerSession<FileServerMessages>> {
        transportRefusal(peer.transport)?.let { return Result.failure(it) }

        return network.requestManager.connect(peer = peer, request = request)
    }

    /**
     * What the OS is still withholding before [transport] could carry an attempt, or `null` when
     * the attempt may go ahead.
     *
     * Not a security control - the OS enforces the grant either way. This is what turns a radio
     * switched off into a report the host can put a button in front of, instead of a dial that
     * times out with nothing to show.
     */
    private suspend fun transportRefusal(transport: SpiId?): RequirementsNotMetException? {
        val kind = transport.asTransportKind() ?: return null
        val report = requirementsChecker.forTransport(kind)

        return RequirementsNotMetException(report).takeUnless { report.isSatisfied }
    }

    /**
     * Writes down how [deviceId] was reached.
     * Best-effort on purpose: remembering a route is convenience,
     * and a storage failure must not turn an operation that succeeded into a failure.
     */
    suspend fun rememberRoute(deviceId: String, endpoint: TransportEndpoint) {
        val route = endpoint.toKnownRoute()

        runCatching {
            storage.trust.recordKnownRoute(
                deviceId = deviceId,
                route = route,
                networkId = networkInfoRepository.currentNetworkId()
            )
        }.fold(
            onSuccess = { Timber.i("saved ${endpoint.address} for $deviceId") },
            onFailure = { Timber.w(it, "could not remember route for $deviceId") }
        )
    }

    private fun findNearbyEndpoint(arguments: PeerLocator.NearbyEndpoint): PeerRef? =
        network.peerDiscovery.peers.value
            .firstNotNullOfOrNull { peer ->
                peer.routes.find {
                    (it.endpoint as? NearbyConnectionsTransportEndpoint)?.endpointId == arguments.endpointId
                }
            }

    private fun PeerLocator.Ip.toPeerRef(): PeerRef = PeerRef.build(
        DirectIpEndpoint(host = host, port = port ?: Constants.DEFAULT_PORT)
    )
}

/**
 * The peer answered and turned the attempt down, so another route would repeat the refusal.
 *
 * A device that answered under the wrong identity is not that refusal: it is someone else holding
 * the route, and the device actually being dialled may still be reachable on another one.
 */
private fun Result<*>.refusedByPeer(): Boolean = when (val e = exceptionOrNull()) {
    is PeerIdentityMismatchException -> false
    else -> e is NetworkException.Handshake
}
