package com.fserver.app.presentation.composable.model

import androidx.compose.runtime.Immutable
import com.fserver.core.network.info.model.NetworkInfo

/**
 * The transport as the connection and search screens draw it.
 *
 * [Wifi.name] is null when Android is withholding the SSID: the card then says the phone is on
 * Wi-Fi but not which one, which is the honest rendering of a location permission the user has
 * not granted — rather than a blank field that reads as a bug.
 */
@Immutable
sealed interface NetworkCardUi {
    data class Wifi(val name: String?) : NetworkCardUi
    data class Mobile(val name: String) : NetworkCardUi
    data object Offline : NetworkCardUi
}

/**
 * @param named whether the network's identifying details are readable — that is, whether
 * `RequirementsChecker.forNetworkInfo()` came back satisfied.
 */
fun NetworkInfo?.toCardUi(named: Boolean): NetworkCardUi = when (this) {
    null -> NetworkCardUi.Offline
    is NetworkInfo.WiFi -> NetworkCardUi.Wifi(name = ssid.takeIf { named })
    is NetworkInfo.Mobile -> NetworkCardUi.Mobile(name = name)
}
