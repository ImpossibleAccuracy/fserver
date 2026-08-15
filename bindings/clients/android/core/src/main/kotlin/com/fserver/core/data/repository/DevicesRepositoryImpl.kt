package com.fserver.core.data.repository

import com.fserver.core.data.datasource.JsonQrCodeParser
import com.fserver.core.data.utils.chainWith
import com.fserver.core.data.utils.runBackgroundJob
import com.fserver.core.domain.Constants
import com.fserver.core.domain.model.ConnectionArguments
import com.fserver.core.domain.model.DetectionMethod
import com.fserver.core.domain.model.ForeignDevice
import com.fserver.core.domain.model.ForeignDevice.Handshake
import com.fserver.core.domain.model.Greeting
import com.fserver.core.domain.model.PendingConfirmation
import com.fserver.core.domain.model.auth.AuthMethod
import com.fserver.core.domain.model.exception.MalformedQrException
import com.fserver.core.domain.model.exception.RequirementsNotMetException
import com.fserver.core.domain.repository.DevicesRepository
import com.fserver.core.domain.repository.RequirementsChecker
import com.fserver.core.domain.repository.ServiceLease
import com.fserver.core.net.InteractivePeerAuthenticator
import com.fserver.core.net.TempMessages
import com.fserver.net.connection.IncomingConnectionsManager
import com.fserver.net.connection.PeerRef
import com.fserver.net.connection.RequestManager
import com.fserver.net.discovery.PeerDiscovery
import com.fserver.net.peer.PublicGreeting
import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.security.auth.AuthRequest
import com.fserver.net.security.auth.pake.PakeAuthMethod
import com.fserver.net.security.auth.sas.SasAuthMethod
import com.fserver.net.spi.SpiId
import com.fserver.net.transport.android.spi.ip.DirectIpEndpoint
import com.fserver.net.transport.android.spi.ip.DirectIpSPI
import com.fserver.net.transport.android.spi.multicastdns.MulticastDnsSPI
import com.fserver.net.transport.android.spi.nearbyconnection.NearbyConnectionsSPI
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
                kind = session.descriptor.kind,
                routes = listOf(session.route)
                    .plus(peer?.routes ?: emptyList())
                    .distinctBy { it.transport },
                foundBy = session.route.transport.asDetectionMethod(),
                lastSeen = Instant.now(),
                handshake = handshake?.let {
                    Handshake(
                        identity = it.identity,
                        negotiated = it.negotiated,
                    )
                },
                hasSession = true,
            )
        }

        profiles.mapTo(result) { (_, profile) ->
            val peer = peersByIds.remove(profile.negotiated.peerDescriptor.deviceId)
            val foundBy = peer?.routes?.first()?.transport

            ForeignDevice(
                deviceId = profile.negotiated.peerDescriptor.deviceId,
                displayName = profile.negotiated.peerDescriptor.displayName,
                kind = profile.negotiated.peerDescriptor.kind,
                routes = listOf(profile.route),
                foundBy = foundBy.asDetectionMethod(),
                lastSeen = Instant.now(),
                handshake = Handshake(
                    identity = profile.identity,
                    negotiated = profile.negotiated,
                ),
                hasSession = false,
            )
        }

        peersByIds.mapTo(result) { (_, peer) ->
            ForeignDevice(
                deviceId = peer.advertised.deviceId,
                displayName = peer.advertised.displayName,
                kind = peer.advertised.kind,
                routes = peer.routes,
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
    override val incoming: Flow<IncomingConnectionsManager.IncomingRequest>
        get() = incomingConnectionsManager.incoming

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

    override suspend fun probe(arguments: ConnectionArguments): Result<Greeting> =
        when (arguments) {
            is ConnectionArguments.DiscoveredDevice -> {
                val device =
                    peerDiscovery.peers.value.find { it.advertised.deviceId == arguments.id }
                if (device == null) {
                    Result.failure(IllegalArgumentException("Device ${arguments.id} not found"))
                } else {
                    requestManager.probe(device)
                }
            }

            is ConnectionArguments.Ip -> requestManager.probe(arguments.toPeerRef())

            is ConnectionArguments.QrPayload -> arguments.toPeerRefOrNull()
                ?.let { requestManager.probe(it) }
                ?: Result.failure(MalformedQrException())
        }.map { it.toDomain() }

    override suspend fun connect(
        arguments: ConnectionArguments,
        method: AuthMethod?,
        password: String?
    ): Result<Unit> {
        val request = AuthRequest(
            method = method?.toAuthMethodId(),
            params = when (method) {
                AuthMethod.Password -> password?.let { PakeAuthMethod.PakeAuthParams(it) }
                else -> null
            }
        )

        return when (arguments) {
            is ConnectionArguments.DiscoveredDevice -> connectKnown(arguments.id, request)
            is ConnectionArguments.Ip -> requestManager.connect(
                arguments.toPeerRef(),
                request = request
            ).map { }

            is ConnectionArguments.QrPayload -> arguments.toPeerRefOrNull()
                ?.let { requestManager.connect(it, request = request).map { } }
                ?: Result.failure(MalformedQrException())
        }
    }

    /** Reconnects to a device already known by [deviceId] — discovered, or previously probed. */
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

    private fun ConnectionArguments.Ip.toPeerRef(): PeerRef = PeerRef.build(
        DirectIpEndpoint(host = host, port = port ?: Constants.DEFAULT_PORT)
    )

    private fun ConnectionArguments.QrPayload.toPeerRefOrNull(): PeerRef? =
        jsonQrCodeParser.parse(payload)?.let {
            PeerRef.build(DirectIpEndpoint(host = it.ip, port = it.port ?: Constants.DEFAULT_PORT))
        }

    override fun advertisingServiceLease(): ServiceLease =
        advertisingController.newLease()
}

private fun SpiId?.asDetectionMethod(): DetectionMethod? = when (this) {
    NearbyConnectionsSPI.ID -> DetectionMethod.Automatic.NearbyConnections
    MulticastDnsSPI.ID -> DetectionMethod.Automatic.MulticastDns
    DirectIpSPI.ID -> DetectionMethod.OnDemand.ManualAddress
    else -> null
}

private fun PublicGreeting.toDomain(): Greeting = Greeting(
    protocolVersions = protocolVersions,
    methods = methods.mapNotNull { it.toDomain() },
)

private fun AuthMethodId.toDomain(): AuthMethod? = when (this) {
    SasAuthMethod.ID -> AuthMethod.ConfirmFingerprint
    AuthMethodId.TransportConfirmation -> AuthMethod.NearbySas
    PakeAuthMethod.ID -> AuthMethod.Password
    else -> null
}

private fun AuthMethod.toAuthMethodId(): AuthMethodId = when (this) {
    AuthMethod.ConfirmFingerprint -> SasAuthMethod.ID
    AuthMethod.NearbySas -> AuthMethodId.TransportConfirmation
    AuthMethod.Password -> PakeAuthMethod.ID
}
