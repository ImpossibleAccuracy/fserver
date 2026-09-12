package com.fserver.core.network.device.impl

import com.fserver.common.utils.runBackgroundJob
import com.fserver.core.network.NetworkController
import com.fserver.core.network.RequirementsNotMetException
import com.fserver.core.network.TransportKind
import com.fserver.core.network.device.DeviceAdvertising
import com.fserver.core.network.impl.asTransportKind
import com.fserver.core.network.impl.spiId
import com.fserver.core.requirement.RequirementsChecker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

internal class DeviceAdvertisingImpl(
    private val network: NetworkController,
    private val requirementsChecker: RequirementsChecker,
) : DeviceAdvertising {

    override val runningMethods: Flow<Set<TransportKind.Automatic>> =
        network.peerDiscovery.activeAdvertisers.map { ids ->
            ids.mapNotNullTo(mutableSetOf()) { it.asTransportKind() as? TransportKind.Automatic }
        }

    override suspend fun start(method: TransportKind.Automatic): Result<Unit> = runBackgroundJob {
        // Same gate as a scan: the radios an advertiser drives are the ones a scan listens on,
        // so it is the same permissions that decide whether it can start at all.
        val requirements = requirementsChecker.forTransport(method)
        if (!requirements.isSatisfied) {
            throw RequirementsNotMetException(requirements)
        }

        network.peerDiscovery.startAdvertising(method.spiId).getOrThrow()
    }

    override suspend fun stop(method: TransportKind.Automatic) =
        network.peerDiscovery.stopAdvertising(method.spiId)

    override suspend fun stopAll() = network.peerDiscovery.stopAdvertising()
}
