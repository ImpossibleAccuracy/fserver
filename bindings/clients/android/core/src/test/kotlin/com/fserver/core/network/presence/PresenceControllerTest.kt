package com.fserver.core.network.presence

import com.fserver.core.network.TransportKind
import com.fserver.core.network.device.DeviceAdvertising
import com.fserver.core.network.device.DeviceDiscovery
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.network.info.model.NetworkInfo
import com.fserver.core.requirement.RequirementReport
import com.fserver.core.requirement.RequirementsChecker
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * The reconcile: what goes on the air, what starts scanning, and who is allowed to decide it.
 *
 * Both halves of the engine are interfaces, so they are plain fakes reporting what they were
 * asked to do; only the requirement checker is mocked, because a report is all this needs from it.
 */
class PresenceControllerTest {

    private val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    private val advertising = FakeAdvertising()
    private val discovery = FakeDiscovery()

    private val requirements = mockk<RequirementsChecker> {
        coEvery { forTransport(any()) } returns RequirementReport.Satisfied
    }

    private val controller = PresenceController(
        devicesRepository = mockk<DevicesRepository> {
            every { this@mockk.advertising } returns this@PresenceControllerTest.advertising
            every { this@mockk.discovery } returns this@PresenceControllerTest.discovery
        },
        requirementsChecker = requirements,
        networkInfoRepository = mockk<NetworkInfoRepository> {
            every { networkInfo } returns MutableStateFlow<NetworkInfo?>(NetworkInfo.Wired)
        },
        backgroundScope = background,
    )

    @After
    fun tearDown() {
        background.cancel()
    }

    @Test
    fun `every ready method goes on the air for a handover that asks`() = runBlocking {
        controller.start()
        controller.handover().setAdvertising(true)

        val onAir = setOf(nextAdvertised(), nextAdvertised())

        assertEquals(
            setOf(TransportKind.NearbyConnections, TransportKind.MulticastDns),
            onAir,
        )
    }

    @Test
    fun `nothing runs while no handover asks for anything`() = runBlocking {
        controller.start()
        controller.handover()

        // Nothing here reports "did not start", so the only way to tell a reconcile that decided
        // against starting from one that has not run yet is to give it a moment and look.
        delay(SettleDelay)
        assertEquals(null, advertising.started.tryReceive().getOrNull())
        assertEquals(null, discovery.started.tryReceive().getOrNull())
    }

    @Test
    fun `scanning is the union of what the handovers ask for`() = runBlocking {
        controller.start()

        val background = controller.handover()
        background.setDiscovery(PresenceController.BackgroundMethods)
        assertEquals(TransportKind.MulticastDns, nextScanned())

        val screen = controller.handover()
        screen.setDiscovery(TransportKind.NearbyConnections, enabled = true)
        assertEquals(TransportKind.NearbyConnections, nextScanned())

        assertEquals(
            setOf(TransportKind.MulticastDns, TransportKind.NearbyConnections),
            controller.discoveryMethods.value,
        )
    }

    @Test
    fun `a method another handover still asks for keeps running`() = runBlocking {
        controller.start()

        val background = controller.handover()
        background.setDiscovery(PresenceController.BackgroundMethods)

        val screen = controller.handover()
        screen.setDiscovery(TransportKind.MulticastDns, enabled = true)

        assertEquals(TransportKind.MulticastDns, nextScanned())

        screen.close()
        delay(SettleDelay)

        assertEquals(setOf(TransportKind.MulticastDns), discovery.running.value)
    }

    @Test
    fun `closing the last handover that asked stops the scan`() = runBlocking {
        controller.start()

        val screen = controller.handover()
        screen.setDiscovery(PresenceController.BackgroundMethods)
        assertEquals(TransportKind.MulticastDns, nextScanned())

        screen.close()
        delay(SettleDelay)

        assertEquals(emptySet<TransportKind>(), discovery.running.value)
        assertEquals(emptySet<TransportKind>(), controller.discoveryMethods.value)
    }

    private suspend fun nextAdvertised(): TransportKind =
        withTimeout(Timeout) { advertising.started.receive() }

    private suspend fun nextScanned(): TransportKind =
        withTimeout(Timeout) { discovery.started.receive() }

    private class FakeAdvertising : DeviceAdvertising {
        val started = Channel<TransportKind.Automatic>(Channel.UNLIMITED)

        private val running = MutableStateFlow<Set<TransportKind.Automatic>>(emptySet())
        override val runningMethods: Flow<Set<TransportKind.Automatic>> = running

        override suspend fun start(method: TransportKind.Automatic): Result<Unit> {
            started.send(method)
            running.update { it + method }
            return Result.success(Unit)
        }

        override suspend fun stop(method: TransportKind.Automatic) {
            running.update { it - method }
        }

        override suspend fun stopAll() {
            running.value = emptySet()
        }
    }

    /** A scan runs until it is cancelled, the way a real one does. */
    private class FakeDiscovery : DeviceDiscovery {
        val started = Channel<TransportKind>(Channel.UNLIMITED)

        val running = MutableStateFlow<Set<TransportKind>>(emptySet())
        override val runningMethods: Flow<Set<TransportKind>> = running

        override suspend fun start(request: TransportKind): Result<Unit> {
            started.send(request)
            running.update { it + request }

            try {
                awaitCancellation()
            } finally {
                running.update { it - request }
            }
        }

        override fun stop(request: TransportKind) {
            running.update { it - request }
        }
    }

    private companion object {
        val Timeout = 5.seconds
        val SettleDelay = 200.milliseconds
    }
}
