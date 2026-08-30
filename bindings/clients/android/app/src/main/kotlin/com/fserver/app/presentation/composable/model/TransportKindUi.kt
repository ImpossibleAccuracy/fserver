package com.fserver.app.presentation.composable.model

import androidx.annotation.StringRes
import com.fserver.app.R
import com.fserver.core.network.TransportKind

/**
 * How a detection method is named and explained to the user.
 *
 * `:core` names methods after the transport they drive (`MulticastDns`); the user is told what
 * that buys them ("By name on the network"), so the copy lives here rather than in the domain.
 */
@get:StringRes
val TransportKind.titleRes: Int
    get() = when (this) {
        TransportKind.MulticastDns -> R.string.method_mdns_title
        TransportKind.NearbyConnections -> R.string.method_nearby_title
        TransportKind.SubnetScan -> R.string.method_subnet_title
        TransportKind.ManualAddress -> R.string.method_manual_title
    }

@get:StringRes
val TransportKind.descriptionRes: Int
    get() = when (this) {
        TransportKind.MulticastDns -> R.string.method_mdns_description
        TransportKind.NearbyConnections -> R.string.method_nearby_description
        TransportKind.SubnetScan -> R.string.method_subnet_description
        TransportKind.ManualAddress -> R.string.method_manual_description
    }

@get:StringRes
val TransportKind.localizedName: Int
    get() = when (this) {
        TransportKind.MulticastDns -> R.string.method_mdns_localized_name
        TransportKind.NearbyConnections -> R.string.method_nearby_localized_name
        TransportKind.SubnetScan -> R.string.method_subnet_localized_name
        TransportKind.ManualAddress -> R.string.method_manual_localized_name
    }
