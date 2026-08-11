package com.fserver.net.discovery

import com.fserver.net.NetLogger
import com.fserver.net.security.EphemeralIdentityStore
import com.fserver.net.spi.Advertiser
import com.fserver.net.spi.DiscoveredEndpoint
import com.fserver.net.spi.DiscoveryProvider
import com.fserver.net.support.LoopbackEndpoint
import com.fserver.net.support.TestDictionary
import com.fserver.net.wire.ProtocolVersions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.seconds

class PeerDiscoveryTest {

    private val identityStore = EphemeralIdentityStore(displayName = "alice")
    private val scope = CoroutineScope(SupervisorJob())

    @After
    fun tearDown() = scope.cancel()

    @Test
    fun `the provider that accepts the params is the one that runs`() = runBlocking {
        val wrong = FakeProvider(Id("wrong"), accepts = { false })
        val right = FakeProvider(Id("right"), accepts = { it is ByAddress })
        val discovery = discovery(providers = listOf(wrong, right))

        discovery.scan(ByAddress).getOrThrow()

        assertEquals(0, wrong.scans.get())
        assertEquals(1, right.scans.get())
    }

    @Test
    fun `params no provider handles fail instead of quietly finding nothing`() = runBlocking {
        val discovery = discovery(providers = listOf(FakeProvider(Id("mdns"), accepts = { false })))

        val outcome = discovery.scan(ByAddress)

        assertTrue(outcome.isFailure)
    }

    @Test
    fun `what a scan finds is both returned and published`() = runBlocking {
        val provider = FakeProvider(
            id = Id("mdns"),
            accepts = { true },
            events = flowOf(appeared("192.168.0.2", "bob-device")),
        )
        val discovery = discovery(providers = listOf(provider))

        val found = discovery.scan(ByAddress).getOrThrow()

        assertEquals(listOf("bob-device"), found.map { it.deviceId })
        val published = withTimeout(TIMEOUT) { discovery.peers.first { it.isNotEmpty() } }
        assertEquals(listOf("bob-device"), published.map { it.deviceId })
    }

    @Test
    fun `a provider already scanning is not started twice`() = runBlocking {
        val provider = FakeProvider(Id("mdns"), accepts = { true }, events = neverEnding())
        val discovery = discovery(providers = listOf(provider))

        val running = scope.launch { discovery.scan(ByAddress) }
        withTimeout(TIMEOUT) { discovery.activeScans.first { Id("mdns") in it } }

        val second = discovery.scan(ByAddress).getOrThrow()

        assertTrue(second.isEmpty())
        assertEquals(1, provider.scans.get())
        running.cancel()
    }

    @Test
    fun `activeScans clears once the provider's flow is done`() = runBlocking {
        val provider = FakeProvider(Id("mdns"), accepts = { true })
        val discovery = discovery(providers = listOf(provider))

        discovery.scan(ByAddress).getOrThrow()

        assertTrue(discovery.activeScans.value.isEmpty())
    }

    @Test
    fun `the advertisement describes this device, and host attributes win`() = runBlocking {
        val advertiser = FakeAdvertiser(Id("mdns"))
        val discovery = discovery(
            advertisers = listOf(advertiser),
            advertisedAttributes = mapOf(
                PeerAttributes.KIND to "phone",
                PeerAttributes.DISPLAY_NAME to "Alice's phone",
            ),
        )

        discovery.startAdvertising().getOrThrow()
        val payload = withTimeout(TIMEOUT) { advertiser.awaitPayload() }

        assertEquals(identityStore.local, payload.identity)
        assertEquals(
            identityStore.local.deviceId,
            payload.attributes[PeerAttributes.DEVICE_ID],
        )
        assertEquals(
            identityStore.local.fingerprint.value,
            payload.attributes[PeerAttributes.FINGERPRINT],
        )
        assertEquals(
            ProtocolVersions.SUPPORTED.last.toString(),
            payload.attributes[PeerAttributes.PROTOCOL_MAX],
        )
        assertEquals(DICTIONARY.id, payload.attributes[PeerAttributes.DICTIONARY_ID])
        assertEquals("phone", payload.attributes[PeerAttributes.KIND])
        // The host's own value is merged over the one :net filled in.
        assertEquals("Alice's phone", payload.attributes[PeerAttributes.DISPLAY_NAME])
    }

    @Test
    fun `advertising twice does not stack a second advertiser`() = runBlocking {
        val advertiser = FakeAdvertiser(Id("mdns"))
        val discovery = discovery(advertisers = listOf(advertiser))

        discovery.startAdvertising().getOrThrow()
        withTimeout(TIMEOUT) { advertiser.awaitPayload() }
        discovery.startAdvertising().getOrThrow()

        assertEquals(1, advertiser.payloads.size)
    }

    @Test
    fun `an advertiser that gave up does not make this device permanently invisible`() =
        runBlocking {
            val advertiser = FakeAdvertiser(Id("mdns"), keepRunning = false)
            val discovery = discovery(advertisers = listOf(advertiser))

            // The first advertiser's flow completes at once; a later call must start a new one
            // rather than see a leftover job and decide it is already advertising.
            withTimeout(TIMEOUT) {
                while (advertiser.payloads.size < 2) {
                    discovery.startAdvertising().getOrThrow()
                    delay(20)
                }
            }
        }

    @Test
    fun `stopAdvertising cancels the advertisers it started`() = runBlocking {
        val advertiser = FakeAdvertiser(Id("mdns"))
        val discovery = discovery(advertisers = listOf(advertiser))

        discovery.startAdvertising().getOrThrow()
        withTimeout(TIMEOUT) { advertiser.awaitPayload() }
        discovery.stopAdvertising()

        withTimeout(TIMEOUT) {
            while (advertiser.cancellations.get() == 0) delay(10)
        }
    }

    // ------------------------------------------------------------------ helpers

    private fun discovery(
        providers: List<DiscoveryProvider> = emptyList(),
        advertisers: List<Advertiser> = emptyList(),
        advertisedAttributes: Map<String, String> = emptyMap(),
    ) = PeerDiscoveryImpl(
        providers = providers,
        advertisers = advertisers,
        identityStore = identityStore,
        dictionary = DICTIONARY,
        advertisedAttributes = advertisedAttributes,
        logger = NetLogger.None,
        scope = scope,
    )

    private fun appeared(address: String, deviceId: String) =
        DiscoveryProvider.Event.Appeared(
            DiscoveredEndpoint(
                endpoint = LoopbackEndpoint(address),
                advertisedName = deviceId,
                attributes = mapOf(PeerAttributes.DEVICE_ID to deviceId),
            )
        )

    private fun neverEnding(): Flow<DiscoveryProvider.Event> = flow { awaitCancellation() }

    private data object ByAddress : DiscoveryProvider.ScanParams

    private class FakeProvider(
        override val id: DiscoveryProvider.Id,
        private val accepts: (DiscoveryProvider.ScanParams) -> Boolean,
        private val events: Flow<DiscoveryProvider.Event> = emptyFlow(),
    ) : DiscoveryProvider {
        val scans = AtomicInteger()

        override fun accepts(params: DiscoveryProvider.ScanParams) = accepts.invoke(params)

        override fun scan(params: DiscoveryProvider.ScanParams): Flow<DiscoveryProvider.Event> =
            flow {
                scans.incrementAndGet()
                emitAll(events)
            }
    }

    private class FakeAdvertiser(
        override val id: DiscoveryProvider.Id,
        private val keepRunning: Boolean = true,
    ) : Advertiser {
        val payloads = CopyOnWriteArrayList<Advertiser.Payload>()
        val cancellations = AtomicInteger()

        override fun advertise(payload: Advertiser.Payload): Flow<Advertiser.Event> = flow {
            payloads += payload
            emit(Advertiser.Event.Started)
            if (keepRunning) {
                try {
                    awaitCancellation()
                } finally {
                    cancellations.incrementAndGet()
                }
            }
        }

        suspend fun awaitPayload(): Advertiser.Payload {
            while (payloads.isEmpty()) delay(10)
            return payloads.first()
        }
    }

    private companion object {
        val DICTIONARY = TestDictionary().descriptor
        val TIMEOUT = 5.seconds

        fun Id(value: String) = DiscoveryProvider.Id(value)
    }
}
