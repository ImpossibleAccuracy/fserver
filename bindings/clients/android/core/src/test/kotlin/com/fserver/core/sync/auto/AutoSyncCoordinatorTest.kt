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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/**
 * What turns a device appearing into a pass, and - more importantly - what does not: a device that
 * is merely still there, and one nothing is registered against.
 *
 * `SyncRunner` is mocked rather than built: it is final and pulls in the whole engine, while all
 * this class does with it is name a device to sync.
 *
 * Runs on the test scheduler and drains after every emission: `combine` conflates, so two lists
 * sent back to back could reach the coordinator as one.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class AutoSyncCoordinatorTest {

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

    private fun TestScope.startCoordinator() {
        AutoSyncCoordinator(
            devicesRepository = mockk<DevicesRepository> { every { devices } returns online },
            networkInfoRepository = mockk<NetworkInfoRepository> { every { networkInfo } returns network },
            storage = storage,
            syncRunner = syncRunner,
            backgroundScope = backgroundScope,
        ).start()
        runCurrent()
    }

    @Test
    fun `paired device appearing syncs its sources`() = runTest {
        storage.sources.upsert(sourceEntry(id = "source-1", deviceId = Peer))
        startCoordinator()

        show(Peer)

        assertEquals(Peer, nextRun())
    }

    @Test
    fun `device already visible is not synced again`() = runTest {
        storage.sources.upsert(sourceEntry(id = "source-1", deviceId = Peer))
        storage.sources.upsert(sourceEntry(id = "source-2", deviceId = Other))
        startCoordinator()

        show(Peer)
        assertEquals(Peer, nextRun())

        // Peer is still on the list here: if staying visible re-triggered, this would report it.
        show(Peer, Other)
        assertEquals(Other, nextRun())
        assertNull(nextRun())
    }

    @Test
    fun `device with no active source is ignored`() = runTest {
        storage.sources.upsert(
            sourceEntry(id = "source-1", deviceId = Pending, status = SourceEntry.Status.Pending),
        )
        storage.sources.upsert(sourceEntry(id = "source-2", deviceId = Peer))
        startCoordinator()

        show(Pending, Stranger)
        show(Pending, Stranger, Peer)

        // The paired device is the first thing to arrive, so neither of the others triggered.
        assertEquals(Peer, nextRun())
        assertNull(nextRun())
    }

    @Test
    fun `device seen again after it left syncs again`() = runTest {
        storage.sources.upsert(sourceEntry(id = "source-1", deviceId = Peer))
        startCoordinator()

        show(Peer)
        assertEquals(Peer, nextRun())

        show()
        show(Peer)

        assertEquals(Peer, nextRun())
    }

    @Test
    fun `network change makes a device that stayed visible a new arrival`() = runTest {
        storage.sources.upsert(sourceEntry(id = "source-1", deviceId = Peer))
        startCoordinator()

        show(Peer)
        assertEquals(Peer, nextRun())

        network.value = NetworkInfo.WiFi("office", "cc:dd")
        runCurrent()

        assertEquals(Peer, nextRun())
    }

    private suspend fun TestScope.show(vararg deviceIds: String) {
        online.emit(*deviceIds)
        runCurrent()
    }

    /** The oldest pass asked for and not yet checked, or null. */
    private fun nextRun(): String? = runs.tryReceive().getOrNull()

    private class FakeOnlineDevices : OnlineDevices {
        private val devices = MutableSharedFlow<List<ForeignDevice>>(replay = 1, extraBufferCapacity = 1)

        override val all: Flow<List<ForeignDevice>> = devices
        override val visible: Flow<List<ForeignDevice>> = devices

        override val connected: Flow<List<ForeignDevice>> = emptyFlow()
        override val handshaken: Flow<List<ForeignDevice>> = emptyFlow()
        override val discovered: Flow<List<ForeignDevice>> = emptyFlow()
        override val offline: Flow<List<ForeignDevice>> = emptyFlow()
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
    }
}
