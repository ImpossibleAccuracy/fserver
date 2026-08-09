package com.fserver.app.presentation.model

import android.Manifest
import android.annotation.SuppressLint
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.fserver.app.R
import com.fserver.core.domain.model.NetworkCapability
import com.fserver.core.domain.model.requirement.Requirement

/** One line of "what is still in the way", as the permissions sheet renders it. */
@Immutable
data class RequirementRowUi(
    @param:StringRes val titleRes: Int,
    @param:StringRes val detailRes: Int,
    /** The app can start the fix; otherwise the row is an explanation with no button. */
    val resolvable: Boolean,
)

/**
 * Expands one [Requirement] into the rows the user sees.
 *
 * [Requirement.RuntimePermission] arrives as a single entry holding every missing permission,
 * because the host requests them in one launch. The user thinks in capabilities rather than in
 * Android permission strings, so they are regrouped here — "Nearby devices" instead of
 * `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE` and `BLUETOOTH_CONNECT` as three rows.
 */
fun Requirement.toRows(): List<RequirementRowUi> = when (this) {
    is Requirement.RuntimePermission -> permissions.toPermissionRows()

    is Requirement.SystemToggle -> listOf(
        when (kind) {
            Requirement.SystemToggle.Kind.BLUETOOTH -> RequirementRowUi(
                R.string.requirement_toggle_bluetooth_title,
                R.string.requirement_toggle_bluetooth_description,
                resolvable = true,
            )

            Requirement.SystemToggle.Kind.WIFI -> RequirementRowUi(
                R.string.requirement_toggle_wifi_title,
                R.string.requirement_toggle_wifi_description,
                resolvable = true,
            )

            Requirement.SystemToggle.Kind.LOCATION_SERVICES -> RequirementRowUi(
                R.string.requirement_toggle_location_title,
                R.string.requirement_toggle_location_description,
                resolvable = true,
            )
        }
    )

    is Requirement.PlayServices -> listOf(
        RequirementRowUi(
            R.string.requirement_play_services_title,
            R.string.requirement_play_services_description,
            resolvable = isUserResolvable,
        )
    )

    is Requirement.MissingHardware -> listOf(
        RequirementRowUi(
            R.string.requirement_hardware_title,
            when (feature) {
                Requirement.MissingHardware.Feature.BLUETOOTH,
                Requirement.MissingHardware.Feature.BLUETOOTH_LE,
                    -> R.string.requirement_hardware_bluetooth_description

                Requirement.MissingHardware.Feature.WIFI,
                Requirement.MissingHardware.Feature.WIFI_DIRECT,
                    -> R.string.requirement_hardware_wifi_description
            },
            resolvable = false,
        )
    )

    is Requirement.MissingNetworkCapability -> listOf(
        RequirementRowUi(
            R.string.requirement_network_title,
            when (capability) {
                NetworkCapability.LOCAL_SUBNET -> R.string.requirement_network_subnet_description
                NetworkCapability.MULTICAST -> R.string.requirement_network_multicast_description
                NetworkCapability.IP_ROUTING -> R.string.requirement_network_routing_description
            },
            resolvable = false,
        )
    )

    Requirement.NoConnectivity -> listOf(
        RequirementRowUi(
            R.string.requirement_no_connectivity_title,
            R.string.requirement_no_connectivity_description,
            resolvable = false,
        )
    )
}

/** Rows for a whole report half, in the order the sheet lists them. */
fun List<Requirement>.toRows(): List<RequirementRowUi> = flatMap { it.toRows() }.distinct()

@SuppressLint("InlinedApi")
private fun List<String>.toPermissionRows(): List<RequirementRowUi> = buildList {
    val remaining = this@toPermissionRows.toMutableSet()

    fun claim(vararg permissions: String, row: RequirementRowUi) {
        if (permissions.any(remaining::remove)) {
            // remove() on the rest so one group never leaves a straggler behind
            permissions.forEach(remaining::remove)
            add(row)
        }
    }

    claim(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        row = RequirementRowUi(
            R.string.requirement_permission_location_title,
            R.string.requirement_permission_location_description,
            resolvable = true,
        ),
    )
    claim(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
        row = RequirementRowUi(
            R.string.requirement_permission_bluetooth_title,
            R.string.requirement_permission_bluetooth_description,
            resolvable = true,
        ),
    )
    claim(
        Manifest.permission.NEARBY_WIFI_DEVICES,
        row = RequirementRowUi(
            R.string.requirement_permission_nearby_wifi_title,
            R.string.requirement_permission_nearby_wifi_description,
            resolvable = true,
        ),
    )
    claim(
        Manifest.permission.ACCESS_LOCAL_NETWORK,
        row = RequirementRowUi(
            R.string.requirement_permission_local_network_title,
            R.string.requirement_permission_local_network_description,
            resolvable = true,
        ),
    )

    // A permission this mapping does not know about is still standing between the user and a
    // working search, so it gets a row rather than disappearing.
    if (remaining.isNotEmpty()) {
        add(
            RequirementRowUi(
                R.string.requirement_permission_other_title,
                R.string.requirement_permission_other_description,
                resolvable = true,
            )
        )
    }
}
