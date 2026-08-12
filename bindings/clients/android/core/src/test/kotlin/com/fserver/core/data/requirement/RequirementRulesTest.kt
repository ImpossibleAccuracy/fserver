package com.fserver.core.data.requirement

import android.Manifest
import com.fserver.core.domain.model.DetectionMethod
import com.fserver.core.domain.model.NetworkCapability
import com.fserver.core.domain.model.NetworkInfo
import com.fserver.core.domain.model.requirement.Requirement
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The SDK-version branching, without a device. Every rule function takes `sdkInt` as a parameter
 * precisely so these run as plain JVM tests.
 */
class RequirementRulesTest {

    @Test
    fun `nearby on api 30 asks for fine location and no bluetooth runtime permissions`() {
        val permissions = nearbyPermissionsAt(sdkInt = 30)

        assertEquals(listOf(Manifest.permission.ACCESS_FINE_LOCATION), permissions)
    }

    @Test
    fun `nearby on api 28 asks for coarse location`() {
        val permissions = nearbyPermissionsAt(sdkInt = 28)

        assertEquals(listOf(Manifest.permission.ACCESS_COARSE_LOCATION), permissions)
    }

    @Test
    fun `nearby on api 31 asks for the bluetooth trio and still for location`() {
        val permissions = nearbyPermissionsAt(sdkInt = 31)

        assertEquals(
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_ADVERTISE,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.ACCESS_FINE_LOCATION,
            ),
            permissions,
        )
    }

    @Test
    fun `nearby on api 33 swaps location for nearby wifi devices`() {
        val permissions = nearbyPermissionsAt(sdkInt = 33)

        assertTrue(Manifest.permission.NEARBY_WIFI_DEVICES in permissions)
        assertFalse(Manifest.permission.ACCESS_FINE_LOCATION in permissions)
        assertFalse(Manifest.permission.ACCESS_COARSE_LOCATION in permissions)
    }

    @Test
    fun `nearby stops asking for location services once the gate is lifted`() {
        val gated =
            detectionRequirementRules(DetectionMethod.Automatic.NearbyConnections, sdkInt = 32)
        val ungated =
            detectionRequirementRules(DetectionMethod.Automatic.NearbyConnections, sdkInt = 33)

        assertTrue(Requirement.SystemToggle.Kind.LOCATION_SERVICES in gated.toggles)
        assertFalse(Requirement.SystemToggle.Kind.LOCATION_SERVICES in ungated.toggles)
    }

    @Test
    fun `a scanned code needs exactly what a typed address needs, and never the camera`() {
        val scanned = DeviceDetectionRequest.QrCode(payload = "{}")
        val typed = DeviceDetectionRequest.ByManualAddress(ipAddress = "192.168.1.14")

        val scannedRules = detectionRequirementRules(scanned.method, sdkInt = 34)
        val typedRules = detectionRequirementRules(typed.method, sdkInt = 34)

        assertEquals(typedRules, scannedRules)
        assertFalse(Manifest.permission.CAMERA in scannedRules.permissions)
    }

    @Test
    fun `mdns over mobile data is missing multicast`() {
        val rules = detectionRequirementRules(DetectionMethod.Automatic.MulticastDns, sdkInt = 34)

        val missing = rules.missingNetworkRequirements(
            NetworkInfo.Mobile(carrierName = "Carrier", networkType = "LTE")
        )

        assertEquals(
            setOf(
                Requirement.MissingNetworkCapability(NetworkCapability.LOCAL_SUBNET),
                Requirement.MissingNetworkCapability(NetworkCapability.MULTICAST),
            ),
            missing,
        )
    }

    @Test
    fun `mdns over wifi is missing nothing`() {
        val rules = detectionRequirementRules(DetectionMethod.Automatic.MulticastDns, sdkInt = 34)

        val missing = rules.missingNetworkRequirements(
            NetworkInfo.WiFi(ssid = "Home_5G", bssid = "00:11:22:33:44:55")
        )

        assertEquals(emptySet<Requirement>(), missing)
    }

    @Test
    fun `no network at all reports connectivity, not a capability list`() {
        val rules = detectionRequirementRules(DetectionMethod.OnDemand.SubnetScan, sdkInt = 34)

        assertEquals(
            setOf(Requirement.NoConnectivity),
            rules.missingNetworkRequirements(network = null)
        )
    }

    @Test
    fun `nearby needs no network, so no network is not a requirement failure`() {
        val rules =
            detectionRequirementRules(DetectionMethod.Automatic.NearbyConnections, sdkInt = 34)

        assertEquals(emptySet<Requirement>(), rules.missingNetworkRequirements(network = null))
    }

    @Test
    fun `reading network details is free before the location gate`() {
        assertEquals(RequirementRules(), networkInfoRequirementRules(sdkInt = 26))
    }

    @Test
    fun `reading network details needs coarse location on api 27 and fine from api 29`() {
        val onApi27 = networkInfoRequirementRules(sdkInt = 27)
        val onApi29 = networkInfoRequirementRules(sdkInt = 29)

        assertEquals(listOf(Manifest.permission.ACCESS_COARSE_LOCATION), onApi27.permissions)
        assertEquals(
            listOf(
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.ACCESS_FINE_LOCATION
            ),
            onApi29.permissions
        )
        assertEquals(listOf(Requirement.SystemToggle.Kind.LOCATION_SERVICES), onApi29.toggles)
    }

    @Test
    fun `bluetooth state is readable only once BLUETOOTH_CONNECT exists to gate it`() {
        val kind = Requirement.SystemToggle.Kind.BLUETOOTH

        assertEquals(null, togglePermission(kind, sdkInt = 30))
        assertEquals(
            Manifest.permission.BLUETOOTH_CONNECT,
            togglePermission(kind, sdkInt = 31),
        )
    }

    @Test
    fun `wifi and location toggles are readable with no permission at all`() {
        assertEquals(null, togglePermission(Requirement.SystemToggle.Kind.WIFI, sdkInt = 34))
        assertEquals(
            null,
            togglePermission(Requirement.SystemToggle.Kind.LOCATION_SERVICES, sdkInt = 34),
        )
    }

    @Test
    fun `nearby survives a device with no wifi direct radio`() {
        val rules =
            detectionRequirementRules(DetectionMethod.Automatic.NearbyConnections, sdkInt = 34)

        // Discovery is BLE; classic and Wi-Fi Direct are bandwidth upgrades, and listing them as
        // hardware would turn a degraded medium into a permanent blocker.
        assertEquals(listOf(Requirement.MissingHardware.Feature.BLUETOOTH_LE), rules.hardware)
    }

    @Test
    fun `a lan method demands no wifi radio, so an ethernet dock is not blocked`() {
        val mdns = detectionRequirementRules(DetectionMethod.Automatic.MulticastDns, sdkInt = 34)
        val subnet = detectionRequirementRules(DetectionMethod.OnDemand.SubnetScan, sdkInt = 34)

        assertEquals(emptyList<Requirement.MissingHardware.Feature>(), mdns.hardware)
        assertEquals(emptyList<Requirement.MissingHardware.Feature>(), subnet.hardware)
        // The Wi-Fi toggle stays as the offered fix; the checker drops it when the transport
        // already supplies the capabilities.
        assertEquals(listOf(Requirement.SystemToggle.Kind.WIFI), mdns.toggles)
    }

    @Test
    fun `lan methods need no permission below api 37`() {
        for (method in lanMethods) {
            assertEquals(
                emptyList<String>(),
                detectionRequirementRules(method, sdkInt = 36).permissions,
            )
        }
    }

    @Test
    fun `local network protection puts every lan method behind a permission on api 37`() {
        for (method in lanMethods) {
            assertEquals(
                listOf(Manifest.permission.ACCESS_LOCAL_NETWORK),
                detectionRequirementRules(method, sdkInt = 37).permissions,
            )
        }
    }

    @Test
    fun `nearby asks for the local network permission too, for its wifi lan medium`() {
        assertFalse(Manifest.permission.ACCESS_LOCAL_NETWORK in nearbyPermissionsAt(sdkInt = 36))
        assertTrue(Manifest.permission.ACCESS_LOCAL_NETWORK in nearbyPermissionsAt(sdkInt = 37))
    }

    private val lanMethods = listOf(
        DetectionMethod.Automatic.MulticastDns,
        DetectionMethod.OnDemand.SubnetScan,
        DetectionMethod.OnDemand.ManualAddress,
    )

    private fun nearbyPermissionsAt(sdkInt: Int): List<String> =
        detectionRequirementRules(DetectionMethod.Automatic.NearbyConnections, sdkInt).permissions
}
