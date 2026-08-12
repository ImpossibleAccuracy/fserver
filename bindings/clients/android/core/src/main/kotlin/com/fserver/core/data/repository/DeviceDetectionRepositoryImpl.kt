package com.fserver.core.data.repository

import com.fserver.core.data.datasource.JsonQrCodeParser
import com.fserver.core.data.utils.runBackgroundJob
import com.fserver.core.domain.Constants
import com.fserver.core.domain.model.DetectionMethod
import com.fserver.core.domain.model.exception.MalformedQrException
import com.fserver.core.domain.model.exception.RequirementsNotMetException
import com.fserver.core.domain.repository.DeviceDetectionRepository
import com.fserver.core.domain.repository.RequirementsChecker
import com.fserver.net.discovery.DiscoveredPeer
import com.fserver.net.discovery.PeerDiscovery
import com.fserver.net.spi.TransportEndpoint
import com.fserver.net.transport.android.spi.ip.DirectIpEndpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import timber.log.Timber

/**
 * Fake detection engine standing in until `:core` is wired up.
 */
internal class DeviceDetectionRepositoryImpl(
    private val peerDiscovery: PeerDiscovery,
    private val requirementsChecker: RequirementsChecker,
    private val jsonQrCodeParser: JsonQrCodeParser,
) : DeviceDetectionRepository {
    override val onlineDevices: Flow<List<DiscoveredPeer>> = peerDiscovery.peers

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

    override fun device(id: String) = peerDiscovery.peer(id)

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

    override suspend fun decodeQrPayload(payload: String): TransportEndpoint {
        val dto = jsonQrCodeParser.parse(payload)
            ?: throw MalformedQrException()

        return DirectIpEndpoint(
            host = dto.ip,
            port = dto.port ?: Constants.DEFAULT_PORT,
        )
    }
}
