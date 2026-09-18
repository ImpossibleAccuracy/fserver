package com.fserver.core.sync.auto

import com.fserver.core.lifecycle.sync.AutoSyncCoordinator
import com.fserver.core.network.device.DevicesRepository
import com.fserver.core.network.device.OnlineDevices
import com.fserver.core.network.device.model.ForeignDevice
import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.network.info.model.NetworkInfo
import com.fserver.core.support.FakeStorage
import com.fserver.core.support.MutableTimeProvider
import com.fserver.core.support.sourceEntry
import com.fserver.core.sync.model.SourceEntry
import com.fserver.core.sync.runner.SyncRunner
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import kotlin.time.Duration.Companion.seconds

/**
 * What turns a device appearing into a pass, and - more importantly - what does not: a device that
 * is merely still there, and one nothing is registered against.
 *
 * `SyncRunner` is mocked rather than built: it is final and pulls in the whole engine, while all
 * this class does with it is name a device to sync.
 */
class AutoSyncCoordinatorTest {

    private val background = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val clock = MutableTimeProvider()
    private val storage = FakeStorage(clock = clock)
    private val online = FakeOnlineDevices()
    private val network = MutableStateFlow<NetworkInfo?>(NetworkInfo.WiFi("home", "aa:bb"))

    /** Device ids the coordinator asked for a pass on, in order. */
    private val runs = Channel<String>(Channel.UNLIMITED)

    private val syncRunner = mockk<SyncRunner> {
        every { runForDeviceAsync(any()) } answers {
            runs.trySend(firstArg())
            Job()
        }
    }

    private val coordinator = AutoSyncCoordinator(
        devicesRepository = mockk<DevicesRepository> { every { devices } returns online },
        networkInfoRepository = mockk<NetworkInfoRepository> { every { networkInfo } returns network },
        storage = storage,
        syncRunner = syncRunner,
        backgroundScope = background,
    )

    @After
    fun tearDown() {
        background.cancel()
    }

    @Test
    fun `paired device appearing syncs its sources`() = runBlocking {
        storage.sources.upsert(sourceEntry(id = "source-1", deviceId = Peer))
        coordinator.start()

        online.emit(Peer)

        assertEquals(Peer, nextRun())
    }

    @Test
    fun `device already visible is not synced again`() = runBlocking {
        storage.sources.upsert(sourceEntry(id = "source-1", deviceId = Peer))
        storage.sources.upsert(sourceEntry(id = "source-2", deviceId = Other))
        coordinator.start()

        online.emit(Peer)
        assertEquals(Peer, nextRun())

        // Peer is still on the list here: if staying visible re-triggered, this would report it.
        online.emit(Peer, Other)
        assertEquals(Other, nextRun())
    }

    @Test
    fun `device with no active source is ignored`() = runBlocking {
        storage.sources.upsert(
            sourceEntry(id = "source-1", deviceId = Pending, status = SourceEntry.Status.Pending),
        )
        storage.sources.upsert(sourceEntry(id = "source-2", deviceId = Peer))
        coordinator.start()

        online.emit(Pending, Stranger)
        online.emit(Pending, Stranger, Peer)

        // The paired device is the first thing to arrive, so neither of the others triggered.
        assertEquals(Peer, nextRun())
    }

    @Test
    fun `device seen again after it left syncs again`() = runBlocking {
        storage.sources.upsert(sourceEntry(id = "source-1", deviceId = Peer))
        coordinator.start()

        online.emit(Peer)
        assertEquals(Peer, nextRun())

        online.emit()
        online.emit(Peer)

        assertEquals(Peer, nextRun())
    }

    @Test
    fun `network change makes a device that stayed visible a new arrival`() = runBlocking {
        storage.sources.upsert(sourceEntry(id = "source-1", deviceId = Peer))
        coordinator.start()

        online.emit(Peer)
        assertEquals(Peer, nextRun())

        network.value = NetworkInfo.WiFi("office", "cc:dd")

        assertEquals(Peer, nextRun())
    }

    private suspend fun nextRun(): String = withTimeout(Timeout) { runs.receive() }

    private class FakeOnlineDevices : OnlineDevices {
        private val devices = MutableSharedFlow<List<ForeignDevice>>(replay = 1, extraBufferCapacity = 1)

        override val all: Flow<List<ForeignDevice>> = devices

        override val connected: Flow<List<ForeignDevice>> = emptyFlow()
        override val handshaken: Flow<List<ForeignDevice>> = emptyFlow()
        override val discovered: Flow<List<ForeignDevice>> = emptyFlow()
        override val known: Flow<List<ForeignDevice>> = emptyFlow()
        override val unknown: Flow<List<ForeignDevice>> = emptyFlow()

        override fun device(id: String): Flow<ForeignDevice?> = emptyFlow()

        suspend fun emit(vararg deviceIds: String) =
            devices.emit(deviceIds.map(::foreignDevice))

        private fun foreignDevice(deviceId: String) = ForeignDevice(
            deviceId = deviceId,
            displayName = deviceId,
            kind = null,
            routes = emptyList(),
            foundBy = null,
            lastSeen = Instant.EPOCH,
            handshake = null,
            hasSession = false,
        )
    }

    private companion object {
        const val Peer = "device-peer"
        const val Other = "device-other"
        const val Pending = "device-pending"
        const val Stranger = "device-stranger"

        val Timeout = 5.seconds
    }
}
