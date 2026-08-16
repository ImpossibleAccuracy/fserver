package com.fserver.app.presentation.model

import androidx.annotation.StringRes
import com.fserver.app.R
import com.fserver.core.network.info.DetectionMethod

/**
 * How a detection method is named and explained to the user.
 *
 * `:core` names methods after the transport they drive (`MulticastDns`); the user is told what
 * that buys them ("By name on the network"), so the copy lives here rather than in the domain.
 */
@get:StringRes
val DetectionMethod.titleRes: Int
    get() = when (this) {
        DetectionMethod.Automatic.MulticastDns -> R.string.method_mdns_title
        DetectionMethod.Automatic.NearbyConnections -> R.string.method_nearby_title
        DetectionMethod.OnDemand.SubnetScan -> R.string.method_subnet_title
        DetectionMethod.OnDemand.ManualAddress -> R.string.method_manual_title
    }

@get:StringRes
val DetectionMethod.descriptionRes: Int
    get() = when (this) {
        DetectionMethod.Automatic.MulticastDns -> R.string.method_mdns_description
        DetectionMethod.Automatic.NearbyConnections -> R.string.method_nearby_description
        DetectionMethod.OnDemand.SubnetScan -> R.string.method_subnet_description
        DetectionMethod.OnDemand.ManualAddress -> R.string.method_manual_description
    }

@get:StringRes
val DetectionMethod.localizedName: Int
    get() = when (this) {
        DetectionMethod.Automatic.MulticastDns -> R.string.method_mdns_localized_name
        DetectionMethod.Automatic.NearbyConnections -> R.string.method_nearby_localized_name
        DetectionMethod.OnDemand.SubnetScan -> R.string.method_subnet_localized_name
        DetectionMethod.OnDemand.ManualAddress -> R.string.method_manual_localized_name
    }

/**
 * The methods the search screen offers.
 *
 * [DetectionMethod.OnDemand.ManualAddress] is excluded: a typed address is not something the
 * screen can go and look for, so it is its own entry on the connection screen instead.
 */
val searchableDetectionMethods: List<DetectionMethod> =
    DetectionMethod.entries.filterNot { it == DetectionMethod.OnDemand.ManualAddress }
