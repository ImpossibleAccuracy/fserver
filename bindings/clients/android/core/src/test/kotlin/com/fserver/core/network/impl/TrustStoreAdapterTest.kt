package com.fserver.core.network.impl

import com.fserver.core.network.auth.AuthMethod
import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.network.info.model.NetworkInfo
import com.fserver.core.support.FakeTrustedDevicesStore
import com.fserver.core.support.MutableTimeProvider
import com.fserver.net.security.auth.sas.SasAuthMethod
import com.fserver.net.security.trust.AuthStrength
import com.fserver.net.security.trust.TrustRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What a completed handshake leaves behind, as `:net` writes it and `:core` reads it back. */
class TrustStoreAdapterTest {

    private val clock = MutableTimeProvider()
    private val store = FakeTrustedDevicesStore()

    private fun adapter(network: NetworkInfo? = NetworkInfo.Wired) = TrustStoreAdapter(
        trustedDevicesStore = store,
        networkInfoRepository = FakeNetworkInfoRepository(network),
        timeProvider = clock,
    )

    @Test
    fun `a pin writes the key and the network it was reached over`() = runTest {
        adapter().pin(record())

        val pinned = store.findByDeviceId(DeviceId).single()
        assertEquals(AuthMethod.ConfirmFingerprint, pinned.method)
        assertEquals(AuthStrength.UserCompared.name, pinned.strength)
        assertEquals("ethernet", store.networks[DeviceId])
    }

    @Test
    fun `a pin on a network the platform will not name records no network`() = runTest {
        adapter(network = null).pin(record())

        assertNull(store.networks[DeviceId])
        assertTrue(store.findByDeviceId(DeviceId).isNotEmpty())
    }

    @Test
    fun `a key is looked up by its bytes, not by the reference`() = runTest {
        adapter().pin(record())

        val found = adapter().find(PublicKey.copyOf())

        assertEquals(DeviceId, found?.deviceId)
        assertEquals(AuthStrength.UserCompared, found?.strength)
    }

    @Test
    fun `a key nobody pinned is not found`() = runTest {
        adapter().pin(record())

        assertNull(adapter().find("some other key".toByteArray()))
    }

    @Test
    fun `every key a device has pinned comes back, which is what a key change is read from`() =
        runTest {
            adapter().pin(record())
            adapter().pin(record(publicKey = "second key".toByteArray()))

            assertEquals(2, adapter().findByDeviceId(DeviceId).size)
        }

    @Test
    fun `re-pinning the same key replaces the record rather than adding a second one`() =
        runTest {
            adapter().pin(record(displayName = "Old name"))
            adapter().pin(record(displayName = "New name"))

            val pinned = adapter().findByDeviceId(DeviceId)
            assertEquals(1, pinned.size)
            assertEquals("New name", pinned.single().displayName)
        }

    @Test
    fun `a handshake that used a method this build does not know does not break the pin`() {
        // TODO: `TrustStoreAdapter.toDevice` calls error() when `AuthMethod.fromId` misses, so a
        //  peer authenticating with a method added later takes the pin path down with it - and the
        //  method set is meant to grow (see AuthMethodId). Decide what a pin should do with an
        //  unknown method (store the raw id, or refuse the pin without throwing) and assert it.
    }

    private fun record(
        publicKey: ByteArray = PublicKey,
        displayName: String = "Peer",
    ) = TrustRecord(
        publicKey = publicKey,
        deviceId = DeviceId,
        displayName = displayName,
        method = SasAuthMethod.ID,
        strength = AuthStrength.UserCompared,
        descriptor = null,
    )

    private class FakeNetworkInfoRepository(private val network: NetworkInfo?) :
        NetworkInfoRepository {
        override val networkInfo: Flow<NetworkInfo?> = flowOf(network)
        override fun refresh() = Unit
    }

    private companion object {
        const val DeviceId = "device-peer"
        val PublicKey: ByteArray = "public-key-bytes".toByteArray()
    }
}
