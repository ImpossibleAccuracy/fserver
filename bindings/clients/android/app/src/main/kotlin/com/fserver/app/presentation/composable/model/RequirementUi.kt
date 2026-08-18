package com.fserver.app.presentation.composable.model

import android.Manifest
import android.annotation.SuppressLint
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.fserver.app.R
import com.fserver.app.presentation.permission.RequirementAction
import com.fserver.core.network.info.model.NetworkCapability
import com.fserver.core.requirement.Requirement

/** One line of "what is still in the way", as the permissions sheet renders it. */
@Immutable
data class RequirementRowUi(
    @param:StringRes val titleRes: Int,
    @param:StringRes val detailRes: Int,
    val action: RequirementAction? = null,
) {
    /** The app can start the fix; otherwise the row is an explanation with no button. */
    val resolvable: Boolean get() = action != null
}

/**
 * Expands one [Requirement] into the rows the user sees.
 *
 * [RuntimePermission] arrives as a single entry holding every missing permission,
 * because the host requests them in one launch. The user thinks in capabilities rather than in
 * Android permission strings, so they are regrouped here — "Nearby devices" instead of
 * `BLUETOOTH_SCAN`, `BLUETOOTH_ADVERTISE` and `BLUETOOTH_CONNECT` as three rows.
 */
fun Requirement.toRows(): List<RequirementRowUi> = when (this) {
    is Requirement.RuntimePermission -> permissions.toPermissionRows()

    is Requirement.SystemToggle -> listOf(
        when (kind) {
            Requirement.SystemToggle.Kind.BLUETOOTH -> RequirementRowUi(
                titleRes = R.string.requirement_toggle_bluetooth_title,
                detailRes = R.string.requirement_toggle_bluetooth_description,
                action = RequirementAction.OpenSettings(settingsAction),
            )

            Requirement.SystemToggle.Kind.WIFI -> RequirementRowUi(
                titleRes = R.string.requirement_toggle_wifi_title,
                detailRes = R.string.requirement_toggle_wifi_description,
                action = RequirementAction.OpenSettings(settingsAction),
            )

            Requirement.SystemToggle.Kind.LOCATION_SERVICES -> RequirementRowUi(
                titleRes = R.string.requirement_toggle_location_title,
                detailRes = R.string.requirement_toggle_location_description,
                action = RequirementAction.OpenSettings(settingsAction),
            )
        }
    )

    is Requirement.PlayServices -> listOf(
        RequirementRowUi(
            titleRes = R.string.requirement_play_services_title,
            detailRes = R.string.requirement_play_services_description,
            action = RequirementAction.ResolvePlayServices.takeIf { isUserResolvable },
        )
    )

    is Requirement.MissingHardware -> listOf(
        RequirementRowUi(
            titleRes = R.string.requirement_hardware_title,
            detailRes = when (feature) {
                Requirement.MissingHardware.Feature.BLUETOOTH,
                Requirement.MissingHardware.Feature.BLUETOOTH_LE,
                    -> R.string.requirement_hardware_bluetooth_description

                Requirement.MissingHardware.Feature.WIFI,
                Requirement.MissingHardware.Feature.WIFI_DIRECT,
                    -> R.string.requirement_hardware_wifi_description
            },
        )
    )

    is Requirement.MissingNetworkCapability -> listOf(
        RequirementRowUi(
            titleRes = R.string.requirement_network_title,
            detailRes = when (capability) {
                NetworkCapability.LOCAL_SUBNET -> R.string.requirement_network_subnet_description
                NetworkCapability.MULTICAST -> R.string.requirement_network_multicast_description
            },
        )
    )

    Requirement.NoConnectivity -> listOf(
        RequirementRowUi(
            titleRes = R.string.requirement_no_connectivity_title,
            detailRes = R.string.requirement_no_connectivity_description,
        )
    )
}

/** Rows for a whole report half, in the order the sheet lists them. */
fun List<Requirement>.toRows(): List<RequirementRowUi> = flatMap { it.toRows() }.distinct()

@SuppressLint("InlinedApi")
private fun List<String>.toPermissionRows(): List<RequirementRowUi> = buildList {
    val remaining = this@toPermissionRows.toMutableSet()

    // A row asks for the permissions of its own group only, so granting one group at a time is
    // possible; the sheet's other button walks the groups in order.
    fun claim(
        vararg permissions: String,
        @StringRes titleRes: Int,
        @StringRes detailRes: Int,
    ) {
        val claimed = permissions.filter(remaining::remove)
        if (claimed.isNotEmpty()) {
            add(
                RequirementRowUi(
                    titleRes,
                    detailRes,
                    RequirementAction.RequestPermissions(claimed),
                )
            )
        }
    }

    claim(
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION,
        titleRes = R.string.requirement_permission_location_title,
        detailRes = R.string.requirement_permission_location_description,
    )
    // One row, not two: the Bluetooth trio and NEARBY_WIFI_DEVICES are all in the platform's
    // NEARBY_DEVICES group, and the system grants a group whole. Split into a Bluetooth row and a
    // Wi-Fi row, the user sees the same dialog twice and the second row clears itself.
    claim(
        Manifest.permission.BLUETOOTH_SCAN,
        Manifest.permission.BLUETOOTH_ADVERTISE,
        Manifest.permission.BLUETOOTH_CONNECT,
        Manifest.permission.NEARBY_WIFI_DEVICES,
        titleRes = R.string.requirement_permission_nearby_devices_title,
        detailRes = R.string.requirement_permission_nearby_devices_description,
    )
    claim(
        Manifest.permission.ACCESS_LOCAL_NETWORK,
        titleRes = R.string.requirement_permission_local_network_title,
        detailRes = R.string.requirement_permission_local_network_description,
    )

    // A permission this mapping does not know about is still standing between the user and a
    // working search, so it gets a row rather than disappearing.
    if (remaining.isNotEmpty()) {
        add(
            RequirementRowUi(
                R.string.requirement_permission_other_title,
                R.string.requirement_permission_other_description,
                RequirementAction.RequestPermissions(remaining.toList()),
            )
        )
    }
}
