package com.fserver.core.network.info.impl

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.wifi.WifiInfo
import android.net.wifi.WifiManager
import android.os.Build
import android.telephony.TelephonyManager
import com.fserver.core.di.BackgroundScope
import com.fserver.core.network.info.NetworkInfoRepository
import com.fserver.core.network.info.model.NetworkInfo
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.launch
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

/**
 * Reads the live link off [ConnectivityManager].
 *
 * Tracks *every* network the app can see, not just the default one, because the default is picked
 * on internet reachability: a router with no uplink leaves Wi-Fi up but keeps cellular default,
 * and that Wi-Fi is exactly the LAN this app exists to use.
 */
internal class NetworkInfoRepositoryImpl(
    context: Context,
    backgroundScope: BackgroundScope,
) : NetworkInfoRepository {
    private val context: Context = context.applicationContext
    private val connectivity = this.context.getSystemService(ConnectivityManager::class.java)

    /**
     * Re-read requests from the host. Dropped when nothing is collecting, which is right: the
     * next collection registers afresh and reads the platform again anyway.
     */
    private val refreshes = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    @OptIn(FlowPreview::class)
    override val networkInfo: Flow<NetworkInfo?> = callbackFlow {
        if (connectivity == null) {
            Timber.w("No ConnectivityManager on this device; reporting offline")
            send(null)
            awaitClose { }
            return@callbackFlow
        }

        // Callbacks arrive on a binder thread, so the map is read and written off-thread.
        val known = ConcurrentHashMap<Network, NetworkCapabilities>()

        fun publish() {
            trySend(known.values.preferred()?.let(::describe))
        }

        fun newCallback() = networkCallback(
            onChanged = { network, caps ->
                known[network] = caps
                publish()
            },
            onGone = { network ->
                known.remove(network)
                publish()
            },
        )

        var callback = newCallback()

        // Seeded from the default network so an offline device answers at once rather than waiting
        // out a callback that never comes. Registration then replays every network within
        // milliseconds, and `debounce` below keeps that burst from reaching collectors.
        // Nameless by construction - this read is always redacted - but it settles "online or
        // not" instantly, and the registration below overwrites it with the named version.
        connectivity.activeNetwork
            ?.let { network ->
                connectivity.getNetworkCapabilities(network)?.let { known[network] = it }
            }
        publish()

        // The builder's defaults already exclude VPNs, which is what we want: a tunnel is reported
        // as the link underneath it, and that link's LAN reach is what discovery goes on.
        connectivity.registerNetworkCallback(NetworkRequest.Builder().build(), callback)

        // Re-registering is the only way to re-read a name. A grant changes nothing about the
        // network, so no callback fires; and from API 31 the SSID is stripped as the capabilities
        // are handed over, against the permissions held at that moment - so every copy already
        // delivered, and everything `getNetworkCapabilities` returns, stays redacted forever.
        launch {
            refreshes.collect {
                connectivity.unregisterNetworkCallback(callback)
                callback = newCallback()
                connectivity.registerNetworkCallback(NetworkRequest.Builder().build(), callback)
            }
        }

        awaitClose { connectivity.unregisterNetworkCallback(callback) }
    }
        .debounce(SETTLE)
        .distinctUntilChanged()
        .shareIn(
            scope = backgroundScope,
            // One registration for however many collectors, held briefly across screen changes.
            started = SharingStarted.WhileSubscribed(
                stopTimeoutMillis = UNSUBSCRIBE_GRACE.inWholeMilliseconds,
                // Once the registration is gone the cached link is a guess about the past.
                replayExpirationMillis = 0,
            ),
            replay = 1,
        )

    override fun refresh() {
        refreshes.tryEmit(Unit)
    }

    private fun describe(caps: NetworkCapabilities): NetworkInfo = when {
        caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> readWiFi(caps)
        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> readMobile()
        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> NetworkInfo.Wired
        else -> NetworkInfo.Other
    }

    /**
     * Names the Wi-Fi link, or reports it unnamed - see [NetworkInfo.WiFi].
     *
     * API 31 moved the read onto the capabilities the callback already carries - see
     * [networkCallback] for the flag that keeps them readable - and deprecated `WifiManager`'s.
     * Both are redacted the same way when the location gate is not passed.
     */
    private fun readWiFi(caps: NetworkCapabilities): NetworkInfo.WiFi {
        val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            caps.transportInfo as? WifiInfo
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(WifiManager::class.java)?.connectionInfo
        }

        return NetworkInfo.WiFi(ssid = info?.readableSsid(), bssid = info?.readableBssid())
    }

    private fun readMobile(): NetworkInfo.Mobile {
        val telephony = context.getSystemService(TelephonyManager::class.java)

        return NetworkInfo.Mobile(
            carrierName = telephony?.networkOperatorName?.takeIf { it.isNotBlank() },
            networkType = telephony?.generation(),
        )
    }
}

/**
 * A callback that reports the network's name when the app is allowed one.
 *
 * From API 31 the SSID inside `NetworkCapabilities` is redacted for everyone who did not ask for
 * it here: `FLAG_INCLUDE_LOCATION_INFO` is what lets the location permission count, and without
 * the flag a fully granted app still reads back `<unknown ssid>`.
 */
private fun networkCallback(
    onChanged: (Network, NetworkCapabilities) -> Unit,
    onGone: (Network) -> Unit,
): ConnectivityManager.NetworkCallback =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        object : ConnectivityManager.NetworkCallback(FLAG_INCLUDE_LOCATION_INFO) {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                onChanged(network, caps)

            override fun onLost(network: Network) = onGone(network)
        }
    } else {
        object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) =
                onChanged(network, caps)

            override fun onLost(network: Network) = onGone(network)
        }
    }

/**
 * The link worth reporting when several are up: a LAN-capable one wins, since it is the only kind
 * discovery can do anything with.
 */
private fun Collection<NetworkCapabilities>.preferred(): NetworkCapabilities? =
    maxByOrNull { caps ->
        when {
            caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> 3
            caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> 3
            caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> 2
            else -> 1
        }
    }

/** Quoted when known, [UNKNOWN_SSID] when the location gate has not been passed. */
private fun WifiInfo.readableSsid(): String? = ssid
    ?.removeSurrounding("\"")
    ?.takeIf { it.isNotBlank() && it != UNKNOWN_SSID }

private fun WifiInfo.readableBssid(): String? = bssid?.takeIf { it != REDACTED_BSSID }

/**
 * Radio generation, or `null` when the platform will not say - `dataNetworkType` is gated on
 * `READ_PHONE_STATE` from API 30, and this module never asks for it.
 */
@Suppress("DEPRECATION") // the pre-LTE constants, kept so an old radio still reports a generation
@SuppressLint("InlinedApi", "MissingPermission")
private fun TelephonyManager.generation(): String? = try {
    when (dataNetworkType) {
        TelephonyManager.NETWORK_TYPE_GPRS,
        TelephonyManager.NETWORK_TYPE_EDGE,
        TelephonyManager.NETWORK_TYPE_CDMA,
        TelephonyManager.NETWORK_TYPE_1xRTT,
        TelephonyManager.NETWORK_TYPE_IDEN,
        TelephonyManager.NETWORK_TYPE_GSM -> "2G"

        TelephonyManager.NETWORK_TYPE_UMTS,
        TelephonyManager.NETWORK_TYPE_EVDO_0,
        TelephonyManager.NETWORK_TYPE_EVDO_A,
        TelephonyManager.NETWORK_TYPE_EVDO_B,
        TelephonyManager.NETWORK_TYPE_HSDPA,
        TelephonyManager.NETWORK_TYPE_HSUPA,
        TelephonyManager.NETWORK_TYPE_HSPA,
        TelephonyManager.NETWORK_TYPE_HSPAP,
        TelephonyManager.NETWORK_TYPE_EHRPD,
        TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"

        TelephonyManager.NETWORK_TYPE_LTE,
        TelephonyManager.NETWORK_TYPE_IWLAN -> "4G"

        TelephonyManager.NETWORK_TYPE_NR -> "5G"

        else -> null
    }
} catch (e: SecurityException) {
    Timber.v(e, "Radio generation is permission-gated; reporting the carrier only")
    null
}

/** What `WifiInfo` hands back in place of the SSID when the location gate has not been passed. */
private const val UNKNOWN_SSID = "<unknown ssid>"

/** Same, for the BSSID. */
private const val REDACTED_BSSID = "02:00:00:00:00:00"

/** Collapses the burst of callbacks that registration replays into one answer. */
private val SETTLE = 150.milliseconds

/** Keeps the registration alive across a screen change instead of tearing it down and rebuilding. */
private val UNSUBSCRIBE_GRACE = 5.seconds
