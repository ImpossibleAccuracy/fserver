package com.fserver.core.network.device.impl

import com.fserver.core.Constants
import com.fserver.core.network.MalformedQrException
import com.fserver.core.network.NetworkController
import com.fserver.core.network.RequirementsNotMetException
import com.fserver.core.network.auth.AuthCredentials
import com.fserver.core.network.auth.AuthMethod
import com.fserver.core.network.auth.Greeting
import com.fserver.core.network.auth.impl.InteractivePeerAuthenticator
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.device.IncomingConnection
import com.fserver.core.network.device.model.DeviceKind
import com.fserver.core.network.device.model.ForeignDevice
import com.fserver.core.network.device.model.ForeignDevice.Handshake
import com.fserver.core.network.device.model.PendingConfirmation
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.impl.SpiRegistry
import com.fserver.core.network.impl.asDetectionMethod
import com.fserver.core.network.impl.spiId
import com.fserver.core.network.info.DetectionMethod
import com.fserver.core.network.info.model.PeerLocator
import com.fserver.core.requirement.RequirementsChecker
import com.fserver.core.store.FServerStorage
import com.fserver.core.utils.chainWith
import com.fserver.core.utils.runBackgroundJob
import com.fserver.net.connection.PeerRef
import com.fserver.net.peer.PublicGreeting
import com.fserver.net.security.NegotiatedParameters
import com.fserver.net.security.auth.AuthRequest
import com.fserver.net.security.auth.pake.PakeAuthMethod
import com.fserver.net.security.identity.PeerIdentity
import com.fserver.net.session.CloseReason
import com.fserver.net.session.PeerSession
import com.fserver.net.spi.TransportEndpoint
import com.fserver.net.transport.android.spi.ip.DirectIpEndpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.Instant

internal class DevicesRepositoryImpl(
    private val network: NetworkController,
    private val requirementsChecker: RequirementsChecker,
    private val jsonQrCodeParser: JsonQrCodeParser,
    private val interactiveAuthenticator: InteractivePeerAuthenticator,
    private val storage: FServerStorage,
) : DevicesRepository {
    override val onlineDevices: Flow<List<ForeignDevice>> = combine(
        network.peerDiscovery.peers,
        network.incomingConnections.sessions, // TODO: Filter out inactive sessions
        network.requestManager.profiles,
    ) { peers, sessions, profiles ->
        val result = mutableListOf<ForeignDevice>()
        val profiles = profiles.toMutableMap()

        val peersByIds = peers.associateByTo(mutableMapOf()) { it.advertised.deviceId }

        sessions.mapTo(result) { session ->
            val peer = peersByIds.remove(session.identity.deviceId)
            val handshake = profiles.remove(session.identity.deviceId)

            ForeignDevice(
                deviceId = session.identity.deviceId,
                displayName = session.descriptor.displayName,
                kind = DeviceKind.fromSerialized(session.descriptor.kind),
                routes = listOf(session.route)
                    .plus(peer?.routes ?: emptyList())
                    .distinctBy { it.transport }
                    .map { it.toDomain() },
                foundBy = session.route.transport.asDetectionMethod(),
                lastSeen = Instant.now(),
                handshake = handshake?.let { it.identity.toDomain(it.negotiated) },
                hasSession = true,
            )
        }

        profiles.mapTo(result) { (_, profile) ->
            val peer = peersByIds.remove(profile.identity.deviceId)
            val foundBy = peer?.routes?.first()?.transport

            ForeignDevice(
                deviceId = profile.identity.deviceId,
                displayName = profile.negotiated.peerDescriptor.displayName,
                kind = DeviceKind.fromSerialized(profile.negotiated.peerDescriptor.kind),
                routes = listOf(profile.route.toDomain()),
                foundBy = foundBy.asDetectionMethod(),
                lastSeen = Instant.now(),
                handshake = profile.identity.toDomain(profile.negotiated),
                hasSession = false,
            )
        }

        peersByIds.mapTo(result) { (_, peer) ->
            ForeignDevice(
                deviceId = peer.advertised.deviceId,
                displayName = peer.advertised.displayName,
                kind = DeviceKind.fromSerialized(peer.advertised.kind),
                routes = peer.routes.map { it.toDomain() },
                foundBy = peer.routes.first().transport.asDetectionMethod(),
                lastSeen = peer.lastSeen,
                handshake = null,
                hasSession = false,
            )
        }

        result
    }

    override val runningScanningMethods: Flow<Set<DetectionMethod>> =
        network.peerDiscovery.activeScans.map { spiId ->
            spiId
                .mapNotNull { id ->
                    DetectionMethod.entries
                        .filterIsInstance<DetectionMethod.Automatic>()
                        .find { it.spiId == id }
                }
                .toSet()
        }

    override val advertisingMethods: Flow<Set<DetectionMethod.Automatic>> =
        network.peerDiscovery.activeAdvertisers.map { ids ->
            ids.mapNotNullTo(mutableSetOf()) { it.asDetectionMethod() as? DetectionMethod.Automatic }
        }
    override val incoming: Flow<IncomingConnection>
        get() = network.incomingConnections.incoming.map { IncomingConnectionWrapper(it) }

    override val pendingConfirmation: Flow<PendingConfirmation?>
        get() = interactiveAuthenticator.pending

    override fun resolvePendingConfirmation(accept: Boolean) =
        interactiveAuthenticator.resolve(accept)

    override fun device(id: String): Flow<ForeignDevice?> = onlineDevices.map { list ->
        list.find { it.deviceId == id }
    }

    override suspend fun disconnect(deviceId: String): Result<Unit> = runCatching {
        network.incomingConnections.session(deviceId)?.close(CloseReason.Normal)
    }

    override suspend fun startDetection(request: DetectionMethod): Result<Unit> = runBackgroundJob {
        // Check before the scanning
        val requirements = requirementsChecker.forDetection(request)
        if (!requirements.isSatisfied) {
            throw RequirementsNotMetException(requirements)
        }

        val scanParams = SpiRegistry.findAutomaticScanParams(request.spiId)
            ?: throw IllegalArgumentException("Cannot start detection for ${request.spiId}: no scan params found")
        network.peerDiscovery.scan(scanParams).getOrThrow()
    }

    override suspend fun startAdvertising(
        method: DetectionMethod.Automatic
    ): Result<Unit> = runBackgroundJob {
        // Same gate as detection: the radios an advertiser drives are the ones a scan listens on,
        // so it is the same permissions that decide whether it can start at all.
        val requirements = requirementsChecker.forDetection(method)
        if (!requirements.isSatisfied) {
            throw RequirementsNotMetException(requirements)
        }

        network.peerDiscovery.startAdvertising(method.spiId).getOrThrow()
    }

    override suspend fun stopAdvertising(method: DetectionMethod.Automatic) =
        network.peerDiscovery.stopAdvertising(method.spiId)

    override suspend fun stopAdvertising() = network.peerDiscovery.stopAdvertising()

    override suspend fun probe(arguments: PeerLocator): Result<Greeting> =
        when (arguments) {
            is PeerLocator.DiscoveredDevice -> {
                val device = network.peerDiscovery.peer(arguments.id).firstOrNull()

                if (device == null) {
                    Result.failure(IllegalArgumentException("Device ${arguments.id} not found"))
                } else {
                    network.requestManager.probe(device)
                }
            }

            is PeerLocator.Ip -> network.requestManager.probe(arguments.toPeerRef())

            is PeerLocator.QrPayload -> arguments.toPeerRefOrNull()
                ?.let { network.requestManager.probe(it) }
                ?: Result.failure(MalformedQrException())
        }
            .onSuccess { probed ->
                if (probed.route.deviceId != PeerRef.UNKNOWN_DEVICE_ID) {
                    rememberRoute(probed.route.deviceId, probed.route.endpoint)
                }
            }
            .map { it.greeting.toDomain() }

    override suspend fun connect(
        arguments: PeerLocator,
        credentials: AuthCredentials?,
    ): Result<Unit> {
        val request = AuthRequest(
            method = credentials?.method?.authMethodId,
            params = when (credentials) {
                is AuthCredentials.Password -> PakeAuthMethod.PakeAuthParams(credentials.password)
                else -> null
            }
        )

        val result = when (arguments) {
            is PeerLocator.DiscoveredDevice -> connectKnown(
                deviceId = arguments.id,
                request = request
            )

            is PeerLocator.Ip -> network.requestManager.connect(
                peer = arguments.toPeerRef(),
                request = request
            )

            is PeerLocator.QrPayload -> arguments.toPeerRefOrNull()
                ?.let { network.requestManager.connect(peer = it, request = request) }
                ?: Result.failure(MalformedQrException())
        }

        return result
            .onSuccess { session ->
                rememberRoute(
                    deviceId = session.identity.deviceId,
                    endpoint = session.route.endpoint
                )
            }
            .map { }
    }

    /** Reconnects to a device already known by [deviceId] - discovered, or previously probed. */
    private suspend fun connectKnown(
        deviceId: String,
        request: AuthRequest
    ): Result<PeerSession<FileServerMessages>> {
        network.incomingConnections.session(deviceId)?.let {
            return Result.success(it)
        }

        return network.peerDiscovery.peers.value
            .find { it.advertised.deviceId == deviceId }
            ?.let {
                network.requestManager.connect(
                    it,
                    request = request
                )
            } // Try to connect by discovered route first
            .chainWith {
                // Fallback to previously probed route, if any.
                network.requestManager.profile(deviceId)
                    ?.let {
                        network.requestManager.connect(it.route, request = request)
                    }
            }
            ?: Result.failure(
                // Device not found anywhere, abort
                IllegalArgumentException("Device $deviceId not found")
            )
    }

    /**
     * Writes down how [deviceId] was reached.
     * Best-effort on purpose: remembering a route is convenience,
     * and a storage failure must not turn an operation that succeeded into a failure.
     */
    private suspend fun rememberRoute(deviceId: String, endpoint: TransportEndpoint) {
        val route = endpoint.toKnownRoute() ?: return

        runCatching { storage.trust.recordKnownRoute(deviceId, route) }
            .onFailure { Timber.w(it, "could not remember route for $deviceId") }
    }

    private fun PeerLocator.Ip.toPeerRef(): PeerRef = PeerRef.build(
        DirectIpEndpoint(host = host, port = port ?: Constants.DEFAULT_PORT)
    )

    private fun PeerLocator.QrPayload.toPeerRefOrNull(): PeerRef? =
        jsonQrCodeParser.parse(payload)?.let {
            PeerRef.build(DirectIpEndpoint(host = it.ip, port = it.port ?: Constants.DEFAULT_PORT))
        }
}

internal fun PublicGreeting.toDomain() = Greeting(
    protocolVersions = protocolVersions,
    methods = methods.mapNotNull { AuthMethod.fromId(it) },
)

private fun PeerRef.toDomain() = ForeignDevice.DeviceRoute(
    address = endpoint.address,
    foundBy = transport.asDetectionMethod(),
)

private fun PeerIdentity.toDomain(negotiated: NegotiatedParameters): Handshake = Handshake(
    fingerprint = fingerprint.value,
    protocolVersion = negotiated.protocolVersion,
    cipherSuite = negotiated.cipherSuite.name,
)
