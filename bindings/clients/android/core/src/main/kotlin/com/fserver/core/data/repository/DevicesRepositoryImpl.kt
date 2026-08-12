package com.fserver.core.data.repository

import com.fserver.core.data.datasource.JsonQrCodeParser
import com.fserver.core.data.utils.chainWith
import com.fserver.core.data.utils.runBackgroundJob
import com.fserver.core.domain.Constants
import com.fserver.core.domain.model.DetectionMethod
import com.fserver.core.domain.model.ForeignDevice
import com.fserver.core.domain.model.ForeignDevice.Handshake
import com.fserver.core.domain.model.exception.MalformedQrException
import com.fserver.core.domain.model.exception.RequirementsNotMetException
import com.fserver.core.domain.repository.DevicesRepository
import com.fserver.core.domain.repository.RequirementsChecker
import com.fserver.core.net.TempMessages
import com.fserver.net.connection.ConnectionManager
import com.fserver.net.connection.PeerRef
import com.fserver.net.discovery.PeerDiscovery
import com.fserver.net.spi.SpiId
import com.fserver.net.transport.android.spi.ip.DirectIpEndpoint
import com.fserver.net.transport.android.spi.ip.DirectIpSPI
import com.fserver.net.transport.android.spi.multicastdns.MulticastDnsSPI
import com.fserver.net.transport.android.spi.nearbyconnection.NearbyConnectionsSPI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import timber.log.Timber
import java.time.Instant

internal class DevicesRepositoryImpl(
    private val peerDiscovery: PeerDiscovery,
    private val connectionManager: ConnectionManager<TempMessages>,
    private val requirementsChecker: RequirementsChecker,
    private val jsonQrCodeParser: JsonQrCodeParser,
) : DevicesRepository {
    override val onlineDevices: Flow<List<ForeignDevice>> = combine(
        peerDiscovery.peers,
        connectionManager.sessions, // TODO: Filter out inactive sessions
        connectionManager.profiles,
    ) { peers, sessions, profiles ->
        val result = mutableListOf<ForeignDevice>()
        val profiles = profiles.toMutableMap()

        val peersByIds = peers.associateByTo(mutableMapOf()) { it.descriptor.deviceId }

        sessions.mapTo(result) { session ->
            val peer = peersByIds.remove(session.descriptor.deviceId)
            val handshake = profiles.remove(session.descriptor.deviceId)

            ForeignDevice(
                descriptor = session.descriptor,
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
            )
        }

        profiles.mapTo(result) { (_, profile) ->
            val peer = peersByIds.remove(profile.negotiated.peerDescriptor.deviceId)
            val foundBy = peer?.routes?.first()?.transport

            ForeignDevice(
                descriptor = profile.negotiated.peerDescriptor,
                routes = listOf(profile.route),
                foundBy = foundBy.asDetectionMethod(),
                lastSeen = Instant.now(),
                handshake = Handshake(
                    identity = profile.identity,
                    negotiated = profile.negotiated,
                ),
            )
        }

        peersByIds.mapTo(result) { (_, peer) ->
            ForeignDevice(
                descriptor = peer.descriptor,
                routes = peer.routes,
                foundBy = peer.routes.first().endpoint.transport.asDetectionMethod(),
                lastSeen = peer.lastSeen,
                handshake = null,
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
    override val incoming: Flow<ConnectionManager.IncomingRequest>
        get() = connectionManager.incoming

    override fun device(id: String): Flow<ForeignDevice?> = onlineDevices.map { list ->
        list.find { it.descriptor.deviceId == id }
    }

    override suspend fun startAdvertising() {
        peerDiscovery.startAdvertising().onFailure {
            Timber.e(it, "Failed to start advertising")
        }
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

    override suspend fun connect(deviceId: String): Result<Unit> {
        if (connectionManager.sessions.value.any { it.descriptor.deviceId == deviceId }) {
            return Result.success(Unit)
        }

        return peerDiscovery.peers.value
            .find { it.descriptor.deviceId == deviceId }
            ?.let { connectionManager.connect(it) } // Try to connect by discovered route first
            .chainWith {
                // Fallback to previously probed route, if any.
                connectionManager.profile(deviceId)
                    ?.let { connectionManager.connect(it.route).map { } }
            }
            ?.map { }
            ?: Result.failure(
                // Device not found anywhere, abort
                IllegalArgumentException("Device $deviceId not found")
            )
    }

    override suspend fun handshakeByDeviceId(deviceId: String): Result<ForeignDevice> {
        connectionManager.profiles.value[deviceId]?.let {
            if (connectionManager.session(deviceId) != null) {
                // Session alive + handshake already done, return the cached handshake result
                return Result.success(it.asForeignDevice())
            }
        }

        val device = peerDiscovery.peers.value.find { it.descriptor.deviceId == deviceId }
            ?: return Result.failure(IllegalArgumentException("Device $deviceId not found"))

        return connectionManager.probe(device)
            .map { it.asForeignDevice() }
    }

    override suspend fun handshake(
        host: String,
        port: Int?
    ): Result<ForeignDevice> {
        val peer = PeerRef.build(
            DirectIpEndpoint(
                host = host,
                port = port ?: Constants.DEFAULT_PORT,
            )
        )

        return connectionManager.probe(peer)
            .map { it.asForeignDevice() }
    }

    override suspend fun handshake(payload: String): Result<ForeignDevice> {
        val dto = jsonQrCodeParser.parse(payload)
            ?: return Result.failure(MalformedQrException())

        val peer = PeerRef.build(
            DirectIpEndpoint(
                host = dto.ip,
                port = dto.port ?: Constants.DEFAULT_PORT,
            )
        )

        return connectionManager.probe(peer)
            .map { it.asForeignDevice() }
    }
}

private fun SpiId?.asDetectionMethod(): DetectionMethod? = when (this) {
    NearbyConnectionsSPI.ID -> DetectionMethod.Automatic.NearbyConnections
    MulticastDnsSPI.ID -> DetectionMethod.Automatic.MulticastDns
    DirectIpSPI.ID -> DetectionMethod.OnDemand.ManualAddress
    else -> null
}

private fun ConnectionManager.Profile.asForeignDevice(): ForeignDevice = ForeignDevice(
    descriptor = negotiated.peerDescriptor,
    routes = listOf(route),
    foundBy = null,
    lastSeen = Instant.now(),
    handshake = Handshake(
        identity = identity,
        negotiated = negotiated,
    )
)
