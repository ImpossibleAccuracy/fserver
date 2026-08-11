package com.fserver.net.discovery

import com.fserver.net.spi.DiscoveredEndpoint
import com.fserver.net.support.LoopbackEndpoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PeerRegistryTest {

    private val registry = PeerRegistry()

    @Test
    fun `one device found on two addresses is one peer with two routes`() {
        registry.record(endpoint("192.168.0.2", DEVICE))
        val peer = registry.record(endpoint("192.168.0.9", DEVICE))

        assertEquals(1, registry.peers.value.size)
        assertEquals(
            listOf("192.168.0.2", "192.168.0.9"),
            peer.routes.map { it.endpoint.address },
        )
    }

    @Test
    fun `re-announcing the same address replaces the route instead of piling up`() {
        registry.record(endpoint("192.168.0.2", DEVICE))
        val peer = registry.record(endpoint("192.168.0.2", DEVICE))

        assertEquals(1, peer.routes.size)
    }

    @Test
    fun `a peer that advertises no device id is keyed by its address`() {
        val peer = registry.record(endpoint("192.168.0.2", attributes = emptyMap()))

        assertEquals("192.168.0.2", peer.deviceId)
        assertTrue(registry.peers.value.containsKey("192.168.0.2"))
    }

    @Test
    fun `losing one route keeps the device while another one remains`() {
        registry.record(endpoint("192.168.0.2", DEVICE))
        registry.record(endpoint("192.168.0.9", DEVICE))

        registry.forgetRoute("192.168.0.2")

        val peer = registry.peers.value.getValue("bob-device")
        assertEquals(listOf("192.168.0.9"), peer.routes.map { it.endpoint.address })
    }

    @Test
    fun `the device disappears only when its last route does`() {
        registry.record(endpoint("192.168.0.2", DEVICE))
        registry.record(endpoint("192.168.0.9", DEVICE))

        registry.forgetRoute("192.168.0.2")
        registry.forgetRoute("192.168.0.9")

        assertTrue(registry.peers.value.isEmpty())
    }

    @Test
    fun `clear drops the routes of one transport and keeps the rest`() {
        registry.record(endpoint("192.168.0.2", DEVICE))
        registry.record(endpoint("192.168.0.9", DEVICE + (PeerAttributes.DEVICE_ID to "other")))

        registry.clear { it.endpoint.address == "192.168.0.2" }

        assertEquals(setOf("other"), registry.peers.value.keys)
    }

    @Test
    fun `an advertisement nobody can parse yields nulls, not a failure`() {
        val peer = registry.record(
            endpoint(
                address = "192.168.0.2",
                attributes = DEVICE + mapOf(
                    PeerAttributes.KIND to "toaster",
                    PeerAttributes.ACCESS to "whatever",
                    PeerAttributes.PROTOCOL_MIN to "1", // no max: an incomplete range is no range
                    PeerAttributes.DICTIONARY_VERSION to "v3",
                ),
            )
        )

        assertNull(peer.kind)
        assertNull(peer.advertised.accessMode)
        assertNull(peer.advertised.protocolVersions)
        assertNull(peer.advertised.dictionaryVersion)
    }

    private fun endpoint(
        address: String,
        attributes: Map<String, String>,
        advertisedName: String = address,
    ) = DiscoveredEndpoint(
        endpoint = LoopbackEndpoint(address),
        advertisedName = advertisedName,
        attributes = attributes,
    )

    private companion object {
        val DEVICE = mapOf(PeerAttributes.DEVICE_ID to "bob-device")
    }
}
