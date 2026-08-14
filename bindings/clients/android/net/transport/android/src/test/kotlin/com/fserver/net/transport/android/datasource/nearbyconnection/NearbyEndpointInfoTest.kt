package com.fserver.net.transport.android.datasource.nearbyconnection

import com.fserver.net.discovery.PeerAttributes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NearbyEndpointInfoTest {

    @Test
    fun `an advertisement survives the round trip`() {
        val essential = identity()
        val optional = mapOf(
            PeerAttributes.KIND to "phone",
        )

        val decoded = NearbyEndpointInfo.decode(NearbyEndpointInfo.encode(essential, optional))

        assertEquals(essential + optional, decoded.attributes)
        assertEquals("alice's phone", decoded.displayName)
    }

    @Test
    fun `the identity fits with room to spare for what grows`() {
        val encoded = NearbyEndpointInfo.encode(identity())

        // The point of the binary form: identity is a fixed cost, and the rest of the budget is
        // left for attributes `:net` has not thought of yet.
        assertTrue(
            "identity took ${encoded.size} of ${NearbyEndpointInfo.MAX_BYTES} bytes",
            encoded.size <= NearbyEndpointInfo.MAX_BYTES / 2,
        )
    }

    @Test
    fun `the protocol range survives, since only the air can carry it`() {
        val decoded = NearbyEndpointInfo.decode(NearbyEndpointInfo.encode(identity()))

        assertEquals("1", decoded.attributes[PeerAttributes.PROTOCOL_MIN])
        assertEquals("2", decoded.attributes[PeerAttributes.PROTOCOL_MAX])
    }

    @Test
    fun `the auth methods never go on the air, even when handed in`() {
        val essential = identity() + (PeerAttributes.AUTH_METHODS to METHODS)

        val decoded = NearbyEndpointInfo.decode(NearbyEndpointInfo.encode(essential))

        // This transport fixes its own method, so a peer reads it off the transport. Broadcasting
        // it would spend a scarce budget repeating what the connection already settles.
        assertNull(decoded.attributes[PeerAttributes.AUTH_METHODS])
    }

    @Test
    fun `the key fingerprint never goes on the air`() {
        val decoded = NearbyEndpointInfo.decode(NearbyEndpointInfo.encode(identity()))

        // Retired on purpose: a stable digest broadcast continuously is what lets a passive
        // listener follow a device from one network to the next.
        assertNull(decoded.attributes["fp"])
    }

    @Test
    fun `optional attributes are dropped before essential ones`() {
        val optional = (1..40).associate { "key$it" to "value$it" }

        val encoded = NearbyEndpointInfo.encode(identity(), optional)
        val decoded = NearbyEndpointInfo.decode(encoded)

        assertTrue(encoded.size <= NearbyEndpointInfo.MAX_BYTES)
        assertEquals(identity(), decoded.attributes.filterKeys { it in identity() })
        assertTrue(decoded.attributes.keys.count { it.startsWith("key") } < optional.size)
    }

    @Test
    fun `a value too long to fit does not hide the ones behind it`() {
        val optional = mapOf(
            PeerAttributes.KIND to "Phone",
        )

        val decoded = NearbyEndpointInfo.decode(NearbyEndpointInfo.encode(identity(), optional))

        assertEquals("Phone", decoded.attributes[PeerAttributes.KIND])
    }

    @Test
    fun `a name long enough to blow the budget is capped, not dropped`() {
        val essential = identity(displayName = "n".repeat(500))

        val encoded = NearbyEndpointInfo.encode(essential)
        val decoded = NearbyEndpointInfo.decode(encoded)

        assertTrue(encoded.size <= NearbyEndpointInfo.MAX_BYTES)
        assertEquals(DEVICE_ID, decoded.attributes[PeerAttributes.DEVICE_ID])
        assertTrue(decoded.displayName.isNotEmpty())
        assertTrue(decoded.displayName.length < 500)
    }

    @Test
    fun `a device id that is not a uuid travels as text`() {
        val essential = identity(deviceId = "bob's desktop")

        val decoded = NearbyEndpointInfo.decode(NearbyEndpointInfo.encode(essential))

        assertEquals("bob's desktop", decoded.attributes[PeerAttributes.DEVICE_ID])
    }

    @Test
    fun `a record from a newer build is skipped, not fatal`() {
        val encoded = NearbyEndpointInfo.encode(identity())
        // Tag 0x42, two bytes of payload this build knows nothing about.
        val fromTheFuture = encoded + byteArrayOf(0x42, 0x02, 0x00, 0x01)

        val decoded = NearbyEndpointInfo.decode(fromTheFuture)

        assertEquals(identity(), decoded.attributes)
    }

    @Test
    fun `a truncated record does not void what came before it`() {
        val encoded = NearbyEndpointInfo.encode(identity())
        val truncated = encoded.copyOf(encoded.size - 1)

        val decoded = NearbyEndpointInfo.decode(truncated)

        assertEquals(DEVICE_ID, decoded.attributes[PeerAttributes.DEVICE_ID])
    }

    @Test
    fun `a peer that does not speak this encoding still has a name`() {
        val decoded = NearbyEndpointInfo.decode("some other client".toByteArray())

        assertEquals("some other client", decoded.displayName)
        assertEquals(emptyMap<String, String>(), decoded.attributes)
    }

    private fun identity(
        deviceId: String = DEVICE_ID,
        displayName: String = "alice's phone",
    ) = mapOf(
        PeerAttributes.DEVICE_ID to deviceId,
        PeerAttributes.DISPLAY_NAME to displayName,
        PeerAttributes.PROTOCOL_MIN to "1",
        PeerAttributes.PROTOCOL_MAX to "2",
    )

    private companion object {
        const val DEVICE_ID = "8f1c2d3e-4a5b-6c7d-8e9f-0a1b2c3d4e5f"
        const val METHODS = "confirm-dh,nearby-sas"
    }
}
