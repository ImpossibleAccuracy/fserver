package com.fserver.core.network.device.impl

import com.fserver.common.utils.runBackgroundJob
import com.fserver.core.network.NetworkController
import com.fserver.core.network.RequirementsNotMetException
import com.fserver.core.network.TransportKind
import com.fserver.core.network.device.DeviceDiscovery
import com.fserver.core.network.impl.SpiRegistry
import com.fserver.core.network.impl.spiId
import com.fserver.core.requirement.RequirementsChecker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class DeviceDiscoveryImpl(
    private val network: NetworkController,
    private val requirementsChecker: RequirementsChecker,
) : DeviceDiscovery {

    // Automatic kinds only: `SubnetScan` and `ManualAddress` share one SPI id, so a running
    // DirectIp scan cannot be attributed to either - see the TODO on `TransportKind.spiId`.
    override val runningMethods: Flow<Set<TransportKind>> =
        network.peerDiscovery.activeScans.map { ids ->
            ids.mapNotNullTo(mutableSetOf<TransportKind>()) { id ->
                TransportKind.entries
                    .filterIsInstance<TransportKind.Automatic>()
                    .find { it.spiId == id }
            }
        }

    override suspend fun start(request: TransportKind): Result<Unit> = runBackgroundJob {
        // Check before the scanning
        val requirements = requirementsChecker.forTransport(request)
        if (!requirements.isSatisfied) {
            throw RequirementsNotMetException(requirements)
        }

        val scanParams = SpiRegistry.findAutomaticScanParams(request.spiId)
            ?: throw IllegalArgumentException("Cannot start detection for ${request.spiId}: no scan params found")
        network.peerDiscovery.scan(scanParams).getOrThrow()
    }

    override fun stop(request: TransportKind) = network.peerDiscovery.stopScan(request.spiId)
}
