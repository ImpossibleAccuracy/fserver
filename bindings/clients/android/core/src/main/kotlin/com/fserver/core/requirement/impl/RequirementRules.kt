package com.fserver.core.requirement.impl

import android.Manifest
import android.annotation.SuppressLint
import com.fserver.core.files.SourceLocation
import com.fserver.core.network.TransportKind
import com.fserver.core.network.info.model.NetworkCapability
import com.fserver.core.network.info.model.NetworkInfo
import com.fserver.core.requirement.Requirement

/**
 * What an operation needs, before anything has been checked against the device.
 *
 * Kept separate from [RequirementsCheckerImpl] on purpose: which permission a given SDK level asks for
 * is the part that is fiddly, version-dependent and worth testing, and none of it needs a
 * `Context`. The checker is then the thin, untestable half that only reads live state.
 */
internal data class RequirementRules(
    val permissions: List<String> = emptyList(),
    val specialPermissions: List<Requirement.SpecialPermission.Kind> = emptyList(),
    val toggles: List<Requirement.SystemToggle.Kind> = emptyList(),
    val hardware: List<Requirement.MissingHardware.Feature> = emptyList(),
    val requiresPlayServices: Boolean = false,
    /** Any network at all will do - every one of them carries IP. */
    val requiresConnectivity: Boolean = false,
    val networkCapabilities: Set<NetworkCapability> = emptySet(),
) {
    /** Whether the transport that is currently up has any say in these rules. */
    val needsNetwork: Boolean get() = requiresConnectivity || networkCapabilities.isNotEmpty()

    /**
     * The network side of these rules, measured against the transport that is currently up.
     *
     * Always a blocker: no permission and no toggle changes what a network carries, only joining a
     * different one does.
     */
    fun missingNetworkRequirements(network: NetworkInfo?): Set<Requirement> = when {
        !needsNetwork -> emptySet()
        network == null -> setOf(Requirement.NoConnectivity)
        else -> (networkCapabilities - network.capabilities).mapTo(mutableSetOf()) {
            Requirement.MissingNetworkCapability(it)
        }
    }
}

/** Requirements of [method] on a device running [sdkInt]. */
internal fun detectionRequirementRules(
    method: TransportKind,
    sdkInt: Int,
): RequirementRules = when (method) {
    TransportKind.NearbyConnections -> RequirementRules(
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
        // Its own radios carry it, so nothing is asked of the IP network.
    )

    TransportKind.MulticastDns -> RequirementRules(
        // NsdManager is unpermissioned below API 37; it still needs a LAN to be on.
        permissions = localNetworkPermissions(sdkInt),
        toggles = listOf(Requirement.SystemToggle.Kind.WIFI),
        networkCapabilities = setOf(NetworkCapability.LOCAL_SUBNET, NetworkCapability.MULTICAST),
    )

    TransportKind.SubnetScan -> RequirementRules(
        // Raw sockets are unpermissioned below API 37; the sweep still needs a LAN to be on.
        permissions = localNetworkPermissions(sdkInt),
        toggles = listOf(Requirement.SystemToggle.Kind.WIFI),
        networkCapabilities = setOf(NetworkCapability.LOCAL_SUBNET),
    )

    // Any route will do - including mobile data, so not even Wi-Fi is asked for. The local network
    // permission is still asked for, because the address the user types is usually a LAN one.
    TransportKind.ManualAddress -> RequirementRules(
        permissions = localNetworkPermissions(sdkInt),
        requiresConnectivity = true,
    )
}

/**
 * Requirements of reaching [location] on a device running [sdkInt].
 *
 * Storage is the area Android has rewritten hardest, and the three answers do not overlap: a
 * source addressed by raw path needs an all-or-nothing grant from Settings, the media library
 * needs one runtime permission per media type, and a document tree needs no app-wide grant at all.
 */
internal fun sourceRequirementRules(
    location: SourceLocation,
    sdkInt: Int,
): RequirementRules = when (location) {
    // App-private storage, no grant exists to ask for
    is SourceLocation.Internal -> RequirementRules()

    // TODO: check if directory is actually accessible
    is SourceLocation.Tree -> RequirementRules()

    SourceLocation.Media -> RequirementRules(permissions = mediaPermissions(sdkInt))

    is SourceLocation.Root,
    is SourceLocation.Directory -> RequirementRules(
        permissions = rawPathPermissions(sdkInt),
        specialPermissions = rawPathSpecialPermissions(sdkInt),
    )
}

/**
 * Reading the media library on a device running [sdkInt].
 *
 * API 33 split the one storage permission into one per media type. On API 34+ the user may answer
 * with a selected subset instead, which grants `READ_MEDIA_VISUAL_USER_SELECTED` and none of these
 * - reported as still missing on purpose, because a source that indexes part of a library would
 * report the rest as deleted.
 */
@SuppressLint("InlinedApi")
private fun mediaPermissions(sdkInt: Int): List<String> = buildList {
    if (sdkInt >= MEDIA_PERMISSIONS_SDK) {
        add(Manifest.permission.READ_MEDIA_IMAGES)
        add(Manifest.permission.READ_MEDIA_VIDEO)
        add(Manifest.permission.READ_MEDIA_AUDIO)
    } else {
        add(Manifest.permission.READ_EXTERNAL_STORAGE)
    }

    // Before scoped storage write went through the file rather than the provider, and that is
    // what `LegacyMediaFileSystem` still does.
    if (sdkInt < SCOPED_STORAGE_SDK) add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
}

/**
 * Reaching a path outside the app's own storage on a device running [sdkInt].
 *
 * From API 30 the runtime pair no longer reaches one and is not asked for. API 29 is the awkward
 * one: the pair is granted but scoped storage already redirects it, so only a device opted into
 * legacy external storage can serve a raw path there - elsewhere on 29 a tree is the way in.
 */
@SuppressLint("InlinedApi")
private fun rawPathPermissions(sdkInt: Int): List<String> =
    if (sdkInt >= ALL_FILES_ACCESS_SDK) {
        emptyList()
    } else {
        listOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
        )
    }

private fun rawPathSpecialPermissions(sdkInt: Int): List<Requirement.SpecialPermission.Kind> =
    if (sdkInt >= ALL_FILES_ACCESS_SDK) {
        listOf(Requirement.SpecialPermission.Kind.ALL_FILES_ACCESS)
    } else {
        emptyList()
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
        // Google's manifest sample stops at API 31 and starts NEARBY_WIFI_DEVICES at 32, but that
        // permission only exists from 33 - so API 32 keeps asking for location,
        // or its Wi-Fi Direct half has no gate to pass at all.
        sdkInt >= FINE_LOCATION_WIFI_INFO_SDK -> add(Manifest.permission.ACCESS_FINE_LOCATION)
        else -> add(Manifest.permission.ACCESS_COARSE_LOCATION)
    }

    // Nearby's WIFI_LAN medium is local network traffic like any other.
    addAll(localNetworkPermissions(sdkInt))
}

@SuppressLint("InlinedApi")
internal fun localNetworkPermissions(sdkInt: Int): List<String> =
    if (sdkInt >= LOCAL_NETWORK_PERMISSION_SDK) {
        listOf(Manifest.permission.ACCESS_LOCAL_NETWORK)
    } else {
        emptyList()
    }

/** API 29 - scoped storage: the storage permissions stop reaching paths outside the app. */
private const val SCOPED_STORAGE_SDK = 29

/** API 30 - `MANAGE_EXTERNAL_STORAGE`, granted from Settings, is the only way back to raw paths. */
private const val ALL_FILES_ACCESS_SDK = 30

/** API 33 - `READ_MEDIA_IMAGES` / `_VIDEO` / `_AUDIO` replace `READ_EXTERNAL_STORAGE`. */
private const val MEDIA_PERMISSIONS_SDK = 33

/** API 27 - `WifiInfo` SSID/BSSID reads start requiring a location permission. */
private const val LOCATION_GATED_WIFI_INFO_SDK = 27

/** API 29 - that permission is raised from coarse to fine, for Wi-Fi info and for BLE scanning. */
private const val FINE_LOCATION_WIFI_INFO_SDK = 29

/** API 31 - `BLUETOOTH_SCAN` / `_ADVERTISE` / `_CONNECT` replace the install-time pair. */
private const val BLUETOOTH_RUNTIME_PERMISSIONS_SDK = 31

/** API 33 - `NEARBY_WIFI_DEVICES` replaces the location gate outright. */
private const val LOCATION_GATE_LIFTED_SDK = 33

/** API 37 - Local Network Protection puts every LAN packet behind `ACCESS_LOCAL_NETWORK`. */
private const val LOCAL_NETWORK_PERMISSION_SDK = 37
