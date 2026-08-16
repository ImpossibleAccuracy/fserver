package com.fserver.core.data.repository

import com.fserver.core.data.datasource.JsonQrCodeParser
import com.fserver.core.data.model.IncomingConnectionWrapper
import com.fserver.core.data.utils.chainWith
import com.fserver.core.data.utils.runBackgroundJob
import com.fserver.core.domain.Constants
import com.fserver.core.domain.model.connection.IncomingConnection
import com.fserver.core.domain.model.connection.PendingConfirmation
import com.fserver.core.domain.model.connection.auth.AuthMethod
import com.fserver.core.domain.model.connection.auth.Greeting
import com.fserver.core.domain.model.connection.device.DeviceKind
import com.fserver.core.domain.model.connection.device.ForeignDevice
import com.fserver.core.domain.model.connection.device.ForeignDevice.Handshake
import com.fserver.core.domain.model.exception.MalformedQrException
import com.fserver.core.domain.model.exception.RequirementsNotMetException
import com.fserver.core.domain.model.network.DetectionMethod
import com.fserver.core.domain.model.network.PeerLocator
import com.fserver.core.domain.repository.DevicesRepository
import com.fserver.core.domain.repository.RequirementsChecker
import com.fserver.core.domain.repository.ServiceLease
import com.fserver.core.net.InteractivePeerAuthenticator
import com.fserver.core.net.TempMessages
import com.fserver.net.connection.IncomingConnectionsManager
import com.fserver.net.connection.PeerRef
import com.fserver.net.connection.RequestManager
import com.fserver.net.discovery.PeerDiscovery
import com.fserver.net.security.NegotiatedParameters
import com.fserver.net.security.auth.AuthRequest
import com.fserver.net.security.auth.pake.PakeAuthMethod
import com.fserver.net.security.identity.PeerIdentity
import com.fserver.net.transport.android.spi.ip.DirectIpEndpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import java.time.Instant

internal class DevicesRepositoryImpl(
    private val peerDiscovery: PeerDiscovery,
    private val requestManager: RequestManager<TempMessages>,
    private val incomingConnectionsManager: IncomingConnectionsManager<TempMessages>,
    private val requirementsChecker: RequirementsChecker,
    private val jsonQrCodeParser: JsonQrCodeParser,
    private val interactiveAuthenticator: InteractivePeerAuthenticator,
) : DevicesRepository {
    private val advertisingController = ServiceLifecycleController(
        startService = { peerDiscovery.startAdvertising() },
        stopService = { peerDiscovery.stopAdvertising() },
    )

    override val onlineDevices: Flow<List<ForeignDevice>> = combine(
        peerDiscovery.peers,
        incomingConnectionsManager.sessions, // TODO: Filter out inactive sessions
        requestManager.profiles,
    ) { peers, sessions, profiles ->
        val result = mutableListOf<ForeignDevice>()
        val profiles = profiles.toMutableMap()

        val peersByIds = peers.associateByTo(mutableMapOf()) { it.advertised.deviceId }

        sessions.mapTo(result) { session ->
            val peer = peersByIds.remove(session.descriptor.deviceId)
            val handshake = profiles.remove(session.descriptor.deviceId)

            ForeignDevice(
                deviceId = session.descriptor.deviceId,
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
            val peer = peersByIds.remove(profile.negotiated.peerDescriptor.deviceId)
            val foundBy = peer?.routes?.first()?.transport

            ForeignDevice(
                deviceId = profile.negotiated.peerDescriptor.deviceId,
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
        peerDiscovery.activeScans.map { spiId ->
            spiId
                .mapNotNull { id ->
                    DetectionMethod.entries
                        .filterIsInstance<DetectionMethod.Automatic>()
                        .find { it.spiId == id }
                }
                .toSet()
        }
    override val incoming: Flow<IncomingConnection>
        get() = incomingConnectionsManager.incoming.map { IncomingConnectionWrapper(it) }

    override val pendingConfirmation: Flow<PendingConfirmation?>
        get() = interactiveAuthenticator.pending

    override fun resolvePendingConfirmation(accept: Boolean) =
        interactiveAuthenticator.resolve(accept)

    override fun device(id: String): Flow<ForeignDevice?> = onlineDevices.map { list ->
        list.find { it.deviceId == id }
    }

    override suspend fun startDetection(request: DetectionMethod): Result<Unit> = runBackgroundJob {
        // Check before the scanning
        val requirements = requirementsChecker.forDetection(request)
        if (!requirements.isSatisfied) {
            throw RequirementsNotMetException(requirements)
        }

        val scanParams = SpiRegistry.findAutomaticScanParams(request.spiId)
            ?: throw IllegalArgumentException("Cannot start detection for ${request.spiId}: no scan params found")
        peerDiscovery.scan(scanParams).getOrThrow()
    }

    override suspend fun probe(arguments: PeerLocator): Result<Greeting> =
        when (arguments) {
            is PeerLocator.DiscoveredDevice -> {
                val device =
                    peerDiscovery.peers.value.find { it.advertised.deviceId == arguments.id }
                if (device == null) {
                    Result.failure(IllegalArgumentException("Device ${arguments.id} not found"))
                } else {
                    requestManager.probe(device)
                }
            }

            is PeerLocator.Ip -> requestManager.probe(arguments.toPeerRef())

            is PeerLocator.QrPayload -> arguments.toPeerRefOrNull()
                ?.let { requestManager.probe(it) }
                ?: Result.failure(MalformedQrException())
        }.map { Greeting.fromNetworkGreeting(it) }

    override suspend fun connect(
        arguments: PeerLocator,
        method: AuthMethod?,
        password: String?
    ): Result<Unit> {
        val request = AuthRequest(
            method = method?.authMethodId,
            params = when (method) {
                AuthMethod.Password -> password?.let { PakeAuthMethod.PakeAuthParams(it) }
                else -> null
            }
        )

        return when (arguments) {
            is PeerLocator.DiscoveredDevice -> connectKnown(arguments.id, request)
            is PeerLocator.Ip -> requestManager.connect(
                arguments.toPeerRef(),
                request = request
            ).map { }

            is PeerLocator.QrPayload -> arguments.toPeerRefOrNull()
                ?.let { requestManager.connect(it, request = request).map { } }
                ?: Result.failure(MalformedQrException())
        }
    }

    /** Reconnects to a device already known by [deviceId] - discovered, or previously probed. */
    private suspend fun connectKnown(deviceId: String, request: AuthRequest): Result<Unit> {
        if (incomingConnectionsManager.sessions.value.any { it.descriptor.deviceId == deviceId }) {
            return Result.success(Unit)
        }

        return peerDiscovery.peers.value
            .find { it.advertised.deviceId == deviceId }
            ?.let {
                requestManager.connect(
                    it,
                    request = request
                )
            } // Try to connect by discovered route first
            .chainWith {
                // Fallback to previously probed route, if any.
                requestManager.profile(deviceId)
                    ?.let { requestManager.connect(it.route, request = request).map { } }
            }
            ?.map { }
            ?: Result.failure(
                // Device not found anywhere, abort
                IllegalArgumentException("Device $deviceId not found")
            )
    }

    private fun PeerLocator.Ip.toPeerRef(): PeerRef = PeerRef.build(
        DirectIpEndpoint(host = host, port = port ?: Constants.DEFAULT_PORT)
    )

    private fun PeerLocator.QrPayload.toPeerRefOrNull(): PeerRef? =
        jsonQrCodeParser.parse(payload)?.let {
            PeerRef.build(DirectIpEndpoint(host = it.ip, port = it.port ?: Constants.DEFAULT_PORT))
        }

    override fun advertisingServiceLease(): ServiceLease =
        advertisingController.newLease()
}

private fun PeerRef.toDomain() = ForeignDevice.DeviceRoute(
    address = endpoint.address,
    foundBy = transport.asDetectionMethod(),
)

private fun PeerIdentity.toDomain(negotiated: NegotiatedParameters): Handshake = Handshake(
    fingerprint = fingerprint.value,
    protocolVersion = negotiated.protocolVersion,
    cipherSuite = negotiated.cipherSuite.name,
)
