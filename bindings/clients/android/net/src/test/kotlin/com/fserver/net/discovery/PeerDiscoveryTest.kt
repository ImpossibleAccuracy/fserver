package com.fserver.net.discovery

import com.fserver.net.NetLogger
import com.fserver.net.config.NetworkConfig
import com.fserver.net.config.NetworkConfigHolder
import com.fserver.net.connection.AdvertisementPolicy
import com.fserver.net.connection.ConnectionPolicy
import com.fserver.net.security.auth.AuthContext
import com.fserver.net.security.auth.AuthMethod
import com.fserver.net.security.auth.AuthMethodId
import com.fserver.net.security.auth.AuthOutcome
import com.fserver.net.security.auth.HandshakeIo
import com.fserver.net.security.trust.AuthStrength
import com.fserver.net.security.identity.EphemeralIdentityStore
import com.fserver.net.spi.Advertiser
import com.fserver.net.spi.DiscoveredEndpoint
import com.fserver.net.spi.DiscoveryProvider
import com.fserver.net.spi.SpiId
import com.fserver.net.support.LoopbackEndpoint
import com.fserver.net.support.TestDictionary
import com.fserver.net.wire.ProtocolVersions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
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
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.milliseconds
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

        assertEquals(listOf("bob-device"), found.map { it.advertised.deviceId })
        val published = withTimeout(TIMEOUT) { discovery.peers.first { it.isNotEmpty() } }
        assertEquals(listOf("bob-device"), published.map { it.advertised.deviceId })
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
    fun `stopScan ends a continuous scan and returns what it found`() = runBlocking {
        val provider = FakeProvider(
            id = Id("mdns"),
            accepts = { true },
            events = flow {
                emit(appeared("192.168.0.2", "bob-device"))
                awaitCancellation()
            },
        )
        val discovery = discovery(providers = listOf(provider))

        val scan = scope.async { discovery.scan(ByAddress) }
        withTimeout(TIMEOUT) { discovery.peers.first { it.isNotEmpty() } }

        discovery.stopScan(Id("mdns"))

        val found = withTimeout(TIMEOUT) { scan.await() }.getOrThrow()
        assertEquals(listOf("bob-device"), found.map { it.advertised.deviceId })
        assertTrue(discovery.activeScans.value.isEmpty())
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

        discovery.startAdvertising(Id("mdns")).getOrThrow()
        val payload = withTimeout(TIMEOUT) { advertiser.awaitPayload() }

        assertEquals(identityStore.local(), payload.identity)
        assertEquals(
            identityStore.local().deviceId,
            payload.attributes[PeerAttributes.DEVICE_ID],
        )
        // A stable key fingerprint on the air is what lets a listener follow a device between
        // networks, so it must not appear under any key.
        assertTrue(identityStore.local().fingerprint.value !in payload.attributes.values)
        assertEquals(
            ProtocolVersions.SUPPORTED.last.toString(),
            payload.attributes[PeerAttributes.PROTOCOL_MAX],
        )
        assertEquals("confirm-dh", payload.attributes[PeerAttributes.AUTH_METHODS])
        assertEquals("phone", payload.attributes[PeerAttributes.KIND])
        // The host's own value is merged over the one :net filled in.
        assertEquals("Alice's phone", payload.attributes[PeerAttributes.DISPLAY_NAME])
    }

    @Test
    fun `a device reachable only by code never announces itself`() = runBlocking {
        val advertiser = FakeAdvertiser(Id("mdns"))
        val discovery = discovery(
            advertisers = listOf(advertiser),
            advertisement = AdvertisementPolicy(enabled = false),
        )

        // Not a failure - being unfindable is the mode working, and the caller has nothing to fix.
        discovery.startAdvertising(Id("mdns")).getOrThrow()

        delay(SETTLE)
        assertNull(advertiser.payloadOrNull())
    }

    @Test
    fun `withholding the name still leaves enough to approach the device`() = runBlocking {
        val advertiser = FakeAdvertiser(Id("mdns"))
        val discovery = discovery(
            advertisers = listOf(advertiser),
            advertisement = AdvertisementPolicy(publishName = false),
        )

        discovery.startAdvertising(Id("mdns")).getOrThrow()
        val payload = withTimeout(TIMEOUT) { advertiser.awaitPayload() }

        assertNull(payload.attributes[PeerAttributes.DISPLAY_NAME])
        assertEquals(
            identityStore.local().deviceId,
            payload.attributes[PeerAttributes.DEVICE_ID],
        )
        assertEquals("confirm-dh", payload.attributes[PeerAttributes.AUTH_METHODS])
    }

    @Test
    fun `advertising twice does not stack a second advertiser`() = runBlocking {
        val advertiser = FakeAdvertiser(Id("mdns"))
        val discovery = discovery(advertisers = listOf(advertiser))

        discovery.startAdvertising(Id("mdns")).getOrThrow()
        withTimeout(TIMEOUT) { advertiser.awaitPayload() }
        discovery.startAdvertising(Id("mdns")).getOrThrow()

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
                    discovery.startAdvertising(Id("mdns")).getOrThrow()
                    delay(20)
                }
            }

            // ... and it does not leave the id behind as still on the air.
            withTimeout(TIMEOUT) { discovery.activeAdvertisers.first { it.isEmpty() } }
            Unit
        }

    @Test
    fun `stopAdvertising cancels the advertisers it started`() = runBlocking {
        val advertiser = FakeAdvertiser(Id("mdns"))
        val discovery = discovery(advertisers = listOf(advertiser))

        discovery.startAdvertising(Id("mdns")).getOrThrow()
        withTimeout(TIMEOUT) { advertiser.awaitPayload() }
        discovery.stopAdvertising()

        withTimeout(TIMEOUT) {
            while (advertiser.cancellations.get() == 0) delay(10)
        }
        assertTrue(discovery.activeAdvertisers.value.isEmpty())
    }

    @Test
    fun `only the advertiser that was asked for goes on the air`() = runBlocking {
        val asked = FakeAdvertiser(Id("mdns"))
        val other = FakeAdvertiser(Id("nearby"))
        val discovery = discovery(advertisers = listOf(asked, other))

        discovery.startAdvertising(Id("mdns")).getOrThrow()
        withTimeout(TIMEOUT) { asked.awaitPayload() }

        delay(SETTLE)
        assertNull(other.payloadOrNull())
        assertEquals(setOf(Id("mdns")), discovery.activeAdvertisers.value)
    }

    @Test
    fun `an advertiser nothing installed fails instead of quietly staying silent`() = runBlocking {
        val discovery = discovery(advertisers = listOf(FakeAdvertiser(Id("mdns"))))

        assertTrue(discovery.startAdvertising(Id("nearby")).isFailure)
    }

    @Test
    fun `stopping one advertiser leaves the others on the air`() = runBlocking {
        val mdns = FakeAdvertiser(Id("mdns"))
        val nearby = FakeAdvertiser(Id("nearby"))
        val discovery = discovery(advertisers = listOf(mdns, nearby))

        discovery.startAdvertising(Id("mdns")).getOrThrow()
        discovery.startAdvertising(Id("nearby")).getOrThrow()
        withTimeout(TIMEOUT) { nearby.awaitPayload() }

        discovery.stopAdvertising(Id("nearby"))

        assertEquals(1, nearby.cancellations.get())
        assertEquals(0, mdns.cancellations.get())
        assertEquals(setOf(Id("mdns")), discovery.activeAdvertisers.value)
    }

    // ------------------------------------------------------------------ helpers

    private fun discovery(
        providers: List<DiscoveryProvider> = emptyList(),
        advertisers: List<Advertiser> = emptyList(),
        advertisedAttributes: Map<String, String> = emptyMap(),
        advertisement: AdvertisementPolicy = AdvertisementPolicy(),
    ) = PeerDiscoveryImpl(
        configHolder = NetworkConfigHolder(
            NetworkConfig(
                dictionary = TestDictionary(),
                identityStore = identityStore,
                policy = ConnectionPolicy(advertisement = advertisement),
                discoveryProviders = providers,
                advertisers = advertisers,
                advertisedAttributes = advertisedAttributes,
                authMethods = listOf(FakeAuthMethod),
                logger = NetLogger.None,
            )
        ),
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
        override val id: SpiId,
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
        override val id: SpiId,
        private val keepRunning: Boolean = true,
    ) : Advertiser {
        val payloads = CopyOnWriteArrayList<Advertiser.Payload>()
        val cancellations = AtomicInteger()

        override suspend fun advertise(payload: Advertiser.Payload): Flow<Advertiser.Event> = flow {
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

        fun payloadOrNull(): Advertiser.Payload? = payloads.firstOrNull()

        suspend fun awaitPayload(): Advertiser.Payload {
            while (payloads.isEmpty()) delay(10)
            return payloads.first()
        }
    }

    /** Advertised unconditionally: id matches what the advertisement assertions expect. */
    private object FakeAuthMethod : AuthMethod {
        override val id: AuthMethodId = AuthMethodId("confirm-dh")
        override val strength = AuthStrength.UserCompared

        override suspend fun run(io: HandshakeIo, context: AuthContext): AuthOutcome =
            error("not exercised by discovery tests")
    }

    private companion object {
        val DICTIONARY = TestDictionary().descriptor
        val SETTLE = 200.milliseconds
        val TIMEOUT = 5.seconds

        fun Id(value: String) = SpiId(value)
    }
}
