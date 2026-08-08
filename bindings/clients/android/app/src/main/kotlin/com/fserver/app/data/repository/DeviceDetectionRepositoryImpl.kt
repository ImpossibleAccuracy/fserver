package com.fserver.app.data.repository

import com.fserver.app.domain.model.DetectionMethod
import com.fserver.app.domain.model.DeviceDetectionRequest
import com.fserver.app.domain.model.FoundDevice
import com.fserver.app.domain.repository.DeviceDetectionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

/**
 * Fake detection engine standing in until `:core` is wired up.
 *
 * The timings and the results are picked to exercise the screen's whole path rather than
 * to be plausible: the automatic pass takes a few seconds and finds nothing — the case a
 * multicast-filtering router produces — so the empty state and the subnet-scan escape
 * hatch both get reached, and only the (slower) sweep returns devices.
 */
class DeviceDetectionRepositoryImpl : DeviceDetectionRepository {
    private val devices = MutableStateFlow<List<FoundDevice>>(emptyList())
    override val onlineDevices: Flow<List<FoundDevice>> = devices.asStateFlow()

    private val runningRequests = MutableStateFlow<Set<DeviceDetectionRequest>>(emptySet())
    override val runningScanningMethods: Flow<Set<DetectionMethod>> = runningRequests
        .map { requests ->
            requests.mapTo(mutableSetOf()) { it.method }
        }

    override suspend fun startDetection(request: DeviceDetectionRequest) {
        if (request in runningRequests.value) return

        when (request) {
            is DeviceDetectionRequest.ByManualAddress -> pingDevice(
                ipAddress = request.ipAddress,
                port = request.port
            )

            is DeviceDetectionRequest.ByMethod -> startByMethodDetection(request)
        }
    }

    private suspend fun startByMethodDetection(
        request: DeviceDetectionRequest.ByMethod,
    ) {
        val method = request.method

        runningRequests.update { it + request }

        try {
            delay(method.fakeDuration)
            publish(method.fakeResults())
        } finally {
            runningRequests.update { it - request }
        }
    }

    private suspend fun pingDevice(ipAddress: String, port: Int?) {
        delay(1.5.seconds)
        publish(
            listOf(
                FoundDevice(
                    id = "$ipAddress:${port ?: SAMPLE_DEFAULT_PORT}",
                    name = ipAddress,
                    source = FoundDevice.Source.ManualEntry(ipAddress, port ?: SAMPLE_DEFAULT_PORT),
                )
            )
        )
    }

    /**
     * Found devices accumulate: a second method finding the same box must not duplicate it.
     * */
    private fun publish(found: List<FoundDevice>) {
        if (found.isEmpty()) return
        devices.update { current -> (current + found).distinctBy { it.id } }
    }
}

private val DetectionMethod.fakeDuration: Duration
    get() = when (this) {
        DetectionMethod.Automatic.DeviceDiscoveryApi -> 3.seconds
        DetectionMethod.Automatic.MulticastDns -> 2.seconds
        DetectionMethod.OnDemand.SubnetScan -> 5.seconds
        DetectionMethod.OnDemand.ManualAddress -> 10.seconds
    }

private const val SAMPLE_DEFAULT_PORT = 8384

private fun DetectionMethod.fakeResults(): List<FoundDevice> = when (this) {
    DetectionMethod.Automatic.DeviceDiscoveryApi -> emptyList()
    DetectionMethod.Automatic.MulticastDns -> emptyList()
    DetectionMethod.OnDemand.SubnetScan -> listOf(
        FoundDevice(
            id = "192.168.1.14:8384",
            name = "MacBook-Pro.local",
            source = FoundDevice.Source.SubnetScan("192.168.1.14", SAMPLE_DEFAULT_PORT),
        ),
        FoundDevice(
            id = "192.168.1.42:8384",
            name = "HOME-NAS",
            source = FoundDevice.Source.SubnetScan("192.168.1.42", SAMPLE_DEFAULT_PORT),
        ),
    )

    DetectionMethod.OnDemand.ManualAddress -> emptyList()
}
