package com.fserver.core.network.device.impl

import com.fserver.common.model.Fingerprint
import com.fserver.common.utils.runBackgroundJob
import com.fserver.core.network.NetworkController
import com.fserver.core.network.RequirementsNotMetException
import com.fserver.core.network.TransportKind
import com.fserver.core.network.device.DeviceAdvertising
import com.fserver.core.network.device.json.JsonQrCodeWriter
import com.fserver.core.network.device.model.DeviceInvitation
import com.fserver.core.network.device.model.KnownRoute
import com.fserver.core.network.impl.asTransportKind
import com.fserver.core.network.impl.spiId
import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.requirement.RequirementsChecker
import com.fserver.core.store.FServerStorage
import com.fserver.net.security.crypto.IdentitySignature
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

internal class DeviceAdvertisingImpl(
    private val network: NetworkController,
    private val requirementsChecker: RequirementsChecker,
    private val networkInfoRepository: NetworkInfoRepository,
    private val qrCodeWriter: JsonQrCodeWriter,
    private val storage: FServerStorage,
) : DeviceAdvertising {

    private val fingerprint by lazy {
        Fingerprint.of(IdentitySignature.encodePublicKey(storage.identity.identityKeyPair.public))
    }

    override val runningMethods: Flow<Set<TransportKind.Automatic>> =
        network.peerDiscovery.activeAdvertisers.map { ids ->
            ids.mapNotNullTo(mutableSetOf()) { it.asTransportKind() as? TransportKind.Automatic }
        }

    override val invitation: Flow<DeviceInvitation?> by lazy {
        combine(networkInfoRepository.localRoutes, runningMethods) { routes, running ->
            buildInvitation(
                routes = routes,
            )
        }
    }

    private suspend fun buildInvitation(
        routes: List<KnownRoute>,
    ): DeviceInvitation? {
        val dialable = routes.filter { it.isDialable }
        if (dialable.isEmpty()) return null

        val device = storage.identity.localDevice()

        return DeviceInvitation(
            payload = qrCodeWriter.write(
                routes = dialable,
                device = device,
                fingerprint = fingerprint,
            ),
            routes = dialable,
            fingerprint = fingerprint,
        )
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
