package com.fserver.core.network.device.impl

import com.fserver.common.exception.MalformedQrException
import com.fserver.core.Constants
import com.fserver.core.network.NetworkController
import com.fserver.core.network.auth.AuthCredentials
import com.fserver.core.network.auth.Greeting
import com.fserver.core.network.auth.impl.InteractivePeerAuthenticator
import com.fserver.core.network.device.DeviceAdvertising
import com.fserver.core.network.device.DeviceDiscovery
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.device.IncomingConnection
import com.fserver.core.network.device.OnlineDevices
import com.fserver.core.network.device.model.PendingConfirmation
import com.fserver.core.network.dictionary.FileServerMessages
import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.network.info.currentNetworkId
import com.fserver.core.network.info.model.PeerLocator
import com.fserver.core.requirement.RequirementsChecker
import com.fserver.core.store.FServerStorage
import com.fserver.common.utils.chainWith
import com.fserver.net.connection.PeerRef
import com.fserver.net.security.auth.AuthRequest
import com.fserver.net.security.auth.pake.PakeAuthMethod
import com.fserver.net.session.CloseReason
import com.fserver.net.session.PeerSession
import com.fserver.net.spi.TransportEndpoint
import com.fserver.net.transport.android.spi.ip.DirectIpEndpoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.map
import timber.log.Timber

internal class DevicesRepositoryImpl(
    private val network: NetworkController,
    private val requirementsChecker: RequirementsChecker,
    private val networkInfoRepository: NetworkInfoRepository,
    private val jsonQrCodeParser: JsonQrCodeParser,
    private val interactiveAuthenticator: InteractivePeerAuthenticator,
    private val storage: FServerStorage,
) : DevicesRepository {

    override val discovery: DeviceDiscovery by lazy {
        DeviceDiscoveryImpl(network = network, requirementsChecker = requirementsChecker)
    }

    override val advertising: DeviceAdvertising by lazy {
        DeviceAdvertisingImpl(network = network, requirementsChecker = requirementsChecker)
    }

    override val devices: OnlineDevices by lazy {
        OnlineDevicesImpl(network = network, storage = storage)
    }

    override val incoming: Flow<IncomingConnection>
        get() = network.incomingConnections.incoming.map { IncomingConnectionWrapper(it) }

    override val pendingConfirmation: Flow<PendingConfirmation?>
        get() = interactiveAuthenticator.pending

    override fun resolvePendingConfirmation(accept: Boolean) =
        interactiveAuthenticator.resolve(accept)

    override suspend fun disconnect(deviceId: String): Result<Unit> = runCatching {
        network.incomingConnections.session(deviceId)?.close(CloseReason.Normal)
    }

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
                rememberNetwork(session.identity.deviceId)
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

    /**
     * Writes down which network this session came up on, so a later "last seen on your home
     * Wi-Fi" is answerable without the device being around.
     *
     * A handshake pins the same thing on its way through `TrustStoreAdapter`; this covers the
     * reconnect that reuses an existing session and never runs one. Best-effort, as [rememberRoute].
     */
    private suspend fun rememberNetwork(deviceId: String) {
        runCatching {
            storage.trust.recordLastNetwork(deviceId, networkInfoRepository.currentNetworkId())
        }.onFailure { Timber.w(it, "could not remember network for $deviceId") }
    }

    private fun PeerLocator.Ip.toPeerRef(): PeerRef = PeerRef.build(
        DirectIpEndpoint(host = host, port = port ?: Constants.DEFAULT_PORT)
    )

    private fun PeerLocator.QrPayload.toPeerRefOrNull(): PeerRef? =
        jsonQrCodeParser.parse(payload)?.let {
            PeerRef.build(DirectIpEndpoint(host = it.ip, port = it.port ?: Constants.DEFAULT_PORT))
        }
}
