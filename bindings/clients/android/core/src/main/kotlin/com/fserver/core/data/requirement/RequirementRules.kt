package com.fserver.core.data.requirement

import android.Manifest
import android.annotation.SuppressLint
import com.fserver.core.domain.model.DetectionMethod
import com.fserver.core.domain.model.NetworkCapability
import com.fserver.core.domain.model.NetworkInfo
import com.fserver.core.domain.model.requirement.Requirement

/**
 * What an operation needs, before anything has been checked against the device.
 *
 * Kept separate from [RequirementsCheckerImpl] on purpose: which permission a given SDK level asks for
 * is the part that is fiddly, version-dependent and worth testing, and none of it needs a
 * `Context`. The checker is then the thin, untestable half that only reads live state.
 */
internal data class RequirementRules(
    val permissions: List<String> = emptyList(),
    val toggles: List<Requirement.SystemToggle.Kind> = emptyList(),
    val hardware: List<Requirement.MissingHardware.Feature> = emptyList(),
    val requiresPlayServices: Boolean = false,
    val networkCapabilities: Set<NetworkCapability> = emptySet(),
) {
    /**
     * The network side of these rules, measured against the transport that is currently up.
     *
     * Always a blocker: no permission and no toggle changes what a network carries, only joining a
     * different one does.
     */
    fun missingNetworkRequirements(network: NetworkInfo?): Set<Requirement> = when {
        networkCapabilities.isEmpty() -> emptySet()
        network == null -> setOf(Requirement.NoConnectivity)
        else -> (networkCapabilities - network.capabilities).mapTo(mutableSetOf()) {
            Requirement.MissingNetworkCapability(it)
        }
    }
}

/** Requirements of [method] on a device running [sdkInt]. */
internal fun detectionRequirementRules(
    method: DetectionMethod,
    sdkInt: Int,
): RequirementRules = when (method) {
    DetectionMethod.Automatic.NearbyConnections -> RequirementRules(
        permissions = nearbyPermissions(sdkInt),
        // Nearby drives all three radios itself; any one of them switched off silently narrows
        // what it can reach, so all three are reported rather than guessing which it will pick.
        toggles = buildList {
            add(Requirement.SystemToggle.Kind.BLUETOOTH)
            add(Requirement.SystemToggle.Kind.WIFI)
            // From API 33 the BLE scan is declared "never for location" and the platform stops
            // gating it on location services.
            if (sdkInt < LOCATION_GATE_LIFTED_SDK) {
                add(Requirement.SystemToggle.Kind.LOCATION_SERVICES)
            }
        },
        hardware = listOf(Requirement.MissingHardware.Feature.BLUETOOTH_LE),
        // Nearby Connections ships inside Play services, not in the platform.
        requiresPlayServices = true,
        networkCapabilities = method.requires,
    )

    DetectionMethod.Automatic.MulticastDns,
    DetectionMethod.OnDemand.SubnetScan -> RequirementRules(
        // NsdManager and raw sockets need no runtime permission; both need a LAN to be on.
        toggles = listOf(Requirement.SystemToggle.Kind.WIFI),
        networkCapabilities = method.requires,
    )

    // Any route will do - including mobile data, so not even Wi-Fi is asked for.
    DetectionMethod.OnDemand.ManualAddress -> RequirementRules(
        networkCapabilities = method.requires,
    )
}

/**
 * Requirements of reading network details on a device running [sdkInt].
 *
 * The transport *type* is free. The identifying details - SSID, BSSID - are the ones Android
 * treats as location data, and it has moved the gate twice: nothing before API 27, coarse location
 * on 27-28, fine location from 29. Location services must also be switched on wherever a
 * permission is asked for, or the read succeeds and returns a redacted placeholder.
 */
internal fun networkInfoRequirementRules(sdkInt: Int): RequirementRules = when {
    sdkInt < LOCATION_GATED_WIFI_INFO_SDK -> RequirementRules()

    sdkInt < FINE_LOCATION_WIFI_INFO_SDK -> RequirementRules(
        permissions = listOf(Manifest.permission.ACCESS_COARSE_LOCATION),
        toggles = listOf(Requirement.SystemToggle.Kind.LOCATION_SERVICES),
    )

    else -> RequirementRules(
        permissions = listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ),
        toggles = listOf(Requirement.SystemToggle.Kind.LOCATION_SERVICES),
    )
}

/**
 * Permission that must already be granted before [kind]'s own on/off state can be read,
 * or `null` when the state is public.
 */
@SuppressLint("InlinedApi")
internal fun togglePermission(
    kind: Requirement.SystemToggle.Kind,
    sdkInt: Int,
): String? = when (kind) {
    Requirement.SystemToggle.Kind.BLUETOOTH ->
        Manifest.permission.BLUETOOTH_CONNECT.takeIf { sdkInt >= BLUETOOTH_RUNTIME_PERMISSIONS_SDK }

    Requirement.SystemToggle.Kind.WIFI,
    Requirement.SystemToggle.Kind.LOCATION_SERVICES -> null
}

/**
 * The permission set Nearby's `P2P_CLUSTER` strategy asks for, which is the widest one: it uses
 * BLE, Bluetooth Classic and Wi-Fi Direct together.
 *
 * See https://developers.google.com/nearby/connections/android/get-started#request_permissions
 */
@SuppressLint("InlinedApi")
private fun nearbyPermissions(sdkInt: Int): List<String> = buildList {
    if (sdkInt >= BLUETOOTH_RUNTIME_PERMISSIONS_SDK) {
        // API 31 split the old install-time BLUETOOTH/BLUETOOTH_ADMIN pair into three runtime
        // permissions, one per action Nearby performs.
        add(Manifest.permission.BLUETOOTH_SCAN)
        add(Manifest.permission.BLUETOOTH_ADVERTISE)
        add(Manifest.permission.BLUETOOTH_CONNECT)
    }

    when {
        // API 33+: NEARBY_WIFI_DEVICES replaces location for the Wi-Fi side, and the Bluetooth
        // side is covered by the permissions above.
        sdkInt >= LOCATION_GATE_LIFTED_SDK -> add(Manifest.permission.NEARBY_WIFI_DEVICES)
        // Fine location became mandatory for BLE scanning in API 29; coarse was enough before.
        sdkInt >= FINE_LOCATION_WIFI_INFO_SDK -> add(Manifest.permission.ACCESS_FINE_LOCATION)
        else -> add(Manifest.permission.ACCESS_COARSE_LOCATION)
    }
}

/** API 27 - `WifiInfo` SSID/BSSID reads start requiring a location permission. */
private const val LOCATION_GATED_WIFI_INFO_SDK = 27

/** API 29 - that permission is raised from coarse to fine, for Wi-Fi info and for BLE scanning. */
private const val FINE_LOCATION_WIFI_INFO_SDK = 29

/** API 31 - `BLUETOOTH_SCAN` / `_ADVERTISE` / `_CONNECT` replace the install-time pair. */
private const val BLUETOOTH_RUNTIME_PERMISSIONS_SDK = 31

/** API 33 - `NEARBY_WIFI_DEVICES` replaces the location gate outright. */
private const val LOCATION_GATE_LIFTED_SDK = 33
