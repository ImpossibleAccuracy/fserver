package com.fserver.app.data.repository

import com.fserver.app.data.detection.connector.DeviceConnector
import com.fserver.app.data.detection.connector.DeviceConnectorFactory
import com.fserver.app.data.detection.scan.DeviceScanEvent
import com.fserver.app.data.detection.scan.DeviceScannerFactory
import com.fserver.app.data.utils.runBackgroundJob
import com.fserver.app.domain.model.DetectionMethod
import com.fserver.app.domain.model.DeviceConnectionCapabilities
import com.fserver.app.domain.model.DeviceDetectionRequest
import com.fserver.app.domain.model.FoundDevice
import com.fserver.app.domain.repository.DeviceDetectionRepository
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.update
import timber.log.Timber

/**
 * Fake detection engine standing in until `:core` is wired up.
 */
internal class DeviceDetectionRepositoryImpl(
    private val deviceScannerFactory: DeviceScannerFactory,
    private val deviceConnectorFactory: DeviceConnectorFactory,
) : DeviceDetectionRepository {
    private val devices = MutableStateFlow<List<FoundDevice>>(emptyList())
    override val onlineDevices: Flow<List<FoundDevice>> = devices.asStateFlow()

    private val runningRequests = MutableStateFlow<Set<DeviceDetectionRequest>>(emptySet())
    override val runningScanningMethods: Flow<Set<DetectionMethod>> = runningRequests
        .map { requests ->
            requests.mapTo(mutableSetOf()) { it.method }
        }

    override fun device(id: String): Flow<FoundDevice?> = devices
        .map { list -> list.firstOrNull { it.id == id } }
        .distinctUntilChanged()

    override suspend fun checkConnectionCapabilities(deviceId: String): Result<DeviceConnectionCapabilities> =
        runBackgroundJob {
            val device = devices.value.firstOrNull { it.id == deviceId }
                ?: throw IllegalArgumentException("Device $deviceId not found")

            val connector = deviceConnectorFactory.fromDevice(device)

            connector.loadCapabilities().getOrThrow()
        }

    override suspend fun startDetection(
        request: DeviceDetectionRequest
    ): Result<List<FoundDevice>> = runBackgroundJob {
        if (request in runningRequests.value) {
            return@runBackgroundJob emptyList()
        }

        runningRequests.update { it + request }

        try {
            val scanner = deviceScannerFactory.fromRequest(request)

            coroutineScope {
                scanner.startScan()
                    .mapNotNull { event ->
                        when (event) {
                            is DeviceScanEvent.Found ->
                                async { resolveDevice(event.connector) }

                            is DeviceScanEvent.Lost -> {
                                revoke(event.deviceId)
                                null
                            }
                        }
                    }
                    .toList()
                    .awaitAll()
                    .filterNotNull()
            }
        } finally {
            runningRequests.update { it - request }
        }
    }

    /**
     * Asks a single discovered peer to describe itself, publishing it on success.
     *
     * @return the described device, or `null` if this peer could not be reached, could not be
     * understood, or was revoked while it was being described.
     */
    private suspend fun resolveDevice(connector: DeviceConnector): FoundDevice? =
        connector.loadDeviceInfo()
            .onSuccess { publish(it) }
            .onFailure { t ->
                currentCoroutineContext().ensureActive()

                // TODO: propagate error to UI layer
                Timber.e(t, "Failed to load device info")
            }
            .getOrNull()

    /**
     * Found devices accumulate: second method finding the same box must not duplicate it.
     */
    private fun publish(found: FoundDevice) {
        devices.update { current -> (current + found).distinctBy { it.id } }
    }

    /**
     * Revoke device from memory storage.
     */
    private fun revoke(deviceId: String) {
        devices.update { current -> current.filterNot { it.id == deviceId } }
    }
}
